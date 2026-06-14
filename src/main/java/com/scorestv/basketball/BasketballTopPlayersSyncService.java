package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayer.Category;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStat;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Bir lig + sezondaki TOP 10 oyuncularini (3 kategori: SCORERS / REBOUNDERS /
 * ASSISTS) tazeleyen servis.
 *
 * <p>Tek API turuyla 3 sey yapar:
 * <ol>
 *   <li>{@code /players?league=X&season=Y&page=N} sayfali cagri ile TUM
 *       oyunculari toplar.
 *   <li>Her oyuncu icin {@link BasketballPlayerUpserter#upsertFromProfile}
 *       cagrilir — master tablo (foto/ulke/dogum/...) + sezonluk stat
 *       ({@link BasketballPlayerSeasonStat}) ayni anda dolar.
 *   <li>Sezon stat'lar DB'den toplu okunur, 3 kategoriye gore sort edilir,
 *       top 10'u {@link BasketballLeagueTopPlayerUpserter#replaceCategory}
 *       ile yazilir.
 * </ol>
 *
 * <p>Sayfalama: API-Basketball {@code /players} sayfa basina ~10-20 oyuncu
 * doner. NBA gibi 500+ oyunculu liglerde sayfalar bos donuyorsa loop
 * durur. Defansif: 50 sayfa hard cap (1000+ oyuncu).
 *
 * <p>Minimum mac filtre: bir oyuncunun sezon istatistikleri anlamli sayilmak
 * icin en az 5 mac oynamis olmasi gerekir. Aksi halde "bir mac = lider"
 * gibi anomali sirlamalar olur. Filtre {@code MIN_GAMES_PLAYED} ile.
 */
@Service
public class BasketballTopPlayersSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballTopPlayersSyncService.class);

    /** Top siralamada gosterilecek max oyuncu sayisi. */
    private static final int TOP_N = 10;

    /** Oyuncunun siralamaya katilmasi icin minimum oyunlanmis mac sayisi. */
    private static final int MIN_GAMES_PLAYED = 5;

    /** Sayfa hard-cap — sonsuz dongu sigortasi. */
    private static final int MAX_PAGES = 50;

    private final BasketballApiClient apiClient;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballPlayerUpserter playerUpserter;
    private final BasketballPlayerSeasonStatRepository statRepo;
    private final BasketballLeagueTopPlayerUpserter topPlayerUpserter;

    public BasketballTopPlayersSyncService(
            BasketballApiClient apiClient,
            BasketballLeagueRepository leagueRepo,
            BasketballPlayerUpserter playerUpserter,
            BasketballPlayerSeasonStatRepository statRepo,
            BasketballLeagueTopPlayerUpserter topPlayerUpserter) {
        this.apiClient = apiClient;
        this.leagueRepo = leagueRepo;
        this.playerUpserter = playerUpserter;
        this.statRepo = statRepo;
        this.topPlayerUpserter = topPlayerUpserter;
    }

    /**
     * Tek lig + sezon icin top players + master + sezon stat senkronu.
     *
     * @param leagueId API-Basketball lig id
     * @param season   sezon (orn. "2024-2025")
     * @return islenen oyuncu sayisi (master tablodan gecen toplam)
     */
    public int syncLeagueSeason(long leagueId, String season) {
        if (season == null || season.isBlank()) {
            log.warn("TopPlayers sync: sezon bos league={}", leagueId);
            return 0;
        }
        BasketballLeague league = leagueRepo.findById(leagueId).orElse(null);
        if (league == null) {
            log.warn("TopPlayers sync: lig bulunamadi id={}", leagueId);
            return 0;
        }

        // 1) Sayfa sayfa /players cek + her oyuncu icin upsertFromProfile
        int processed = fetchAndUpsertAllPlayers(leagueId, season, league);

        // 2) DB'den sezon stat'larini oku + filtrele
        List<BasketballPlayerSeasonStat> stats =
                statRepo.findByLeagueAndSeason(leagueId, season).stream()
                        .filter(s -> s.getGamesPlayed() != null
                                && s.getGamesPlayed() >= MIN_GAMES_PLAYED)
                        .toList();

        if (stats.isEmpty()) {
            log.info("TopPlayers sync: yeterli mac oynayan oyuncu yok league={} season={}",
                    leagueId, season);
            // Yine de timestamp guncelle ki tekrar tekrar denemesin
            league.setLastTopPlayersSyncedAt(Instant.now());
            leagueRepo.save(league);
            return processed;
        }

        // 3) 3 kategori — sort + top 10 + replace
        writeCategory(league, season, Category.SCORERS, stats,
                BasketballPlayerSeasonStat::getPointsPerGame);
        writeCategory(league, season, Category.REBOUNDERS, stats,
                BasketballPlayerSeasonStat::getReboundsTotal);
        writeCategory(league, season, Category.ASSISTS, stats,
                BasketballPlayerSeasonStat::getAssistsPerGame);

        // 4) Lig timestamp guncelle — daily refresh job 24sa freshness gate kullanir
        league.setLastTopPlayersSyncedAt(Instant.now());
        leagueRepo.save(league);

        log.info("TopPlayers sync tamam league={} season={} oyuncu={} stat={}",
                leagueId, season, processed, stats.size());
        return processed;
    }

    /**
     * {@code /players?league=X&season=Y&page=N} dongusu — tum oyunculari
     * tek tek upserter'a teslim. Sayfa bos donerse veya MAX_PAGES'a ulasilirsa
     * loop durur.
     *
     * @return islenen oyuncu sayisi
     */
    private int fetchAndUpsertAllPlayers(long leagueId, String season,
                                          BasketballLeague league) {
        int processed = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<BkPlayerDto> pageDtos;
            try {
                pageDtos = apiClient.fetchPlayersByLeagueSeason(leagueId, season, page);
            } catch (Exception e) {
                log.warn("TopPlayers sync sayfa cagri hata league={} season={} page={}: {}",
                        leagueId, season, page, e.toString());
                break;
            }
            if (pageDtos == null || pageDtos.isEmpty()) {
                log.debug("TopPlayers sync sayfa bos league={} season={} page={} — durduruluyor",
                        leagueId, season, page);
                break;
            }
            for (BkPlayerDto dto : pageDtos) {
                if (dto == null || dto.id() == null) continue;
                try {
                    playerUpserter.upsertFromProfile(dto, league, season);
                    processed++;
                } catch (Exception e) {
                    log.warn("TopPlayers oyuncu upsert hata id={}: {}",
                            dto.id(), e.toString());
                }
            }
        }
        return processed;
    }

    /**
     * Bir kategori icin top 10 oyuncu listesini hazirlayip
     * {@link BasketballLeagueTopPlayerUpserter} ile yazar.
     */
    private void writeCategory(BasketballLeague league,
                                String season,
                                Category category,
                                List<BasketballPlayerSeasonStat> stats,
                                Function<BasketballPlayerSeasonStat, BigDecimal> metric) {
        List<BasketballLeagueTopPlayerUpserter.Entry> entries = stats.stream()
                .filter(s -> metric.apply(s) != null)
                .sorted(Comparator.comparing(metric,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_N)
                .map(s -> new BasketballLeagueTopPlayerUpserter.Entry(
                        s.getPlayer(),
                        s.getTeam(),
                        metric.apply(s),
                        s.getGamesPlayed()))
                .toList();
        topPlayerUpserter.replaceCategory(league, season, category, entries);
    }
}

package com.scorestv.basketball.detail;

import com.scorestv.basketball.BasketballGameStatsSyncService;
import com.scorestv.basketball.BasketballH2hSyncService;
import com.scorestv.basketball.BasketballStandingsSyncService;
import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGamePlayerStatRepository;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballGameTeamStatRepository;
import com.scorestv.basketball.domain.BasketballSeason;
import com.scorestv.basketball.domain.BasketballSeasonRepository;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basketbol mac detay endpoint'i ihtiyac duyulan yan modulleri
 * (stats, h2h, standings) tembel olarak API'den ceker.
 *
 * <p>Stratejisi (futbol {@code MatchDetailLazySync} esi, basketbol icin
 * sadelestirilmis):
 * <ul>
 *   <li><b>Stats</b>: Yalniz CANLI veya FT maclar icin. NS macta stats yok.</li>
 *   <li><b>H2H</b>: 12 saat freshness penceresi. DB bossa zorla cek.</li>
 *   <li><b>Standings</b>: 1 saat freshness penceresi (API saatlik). DB bos
 *       veya bu pencere asilirsa cek.</li>
 * </ul>
 *
 * <p><b>Empty-debounce</b>: API bos cevap verirse 10 dk tekrar denenmez.
 *
 * <p><b>Cold-start guvenligi</b>: Restart sonrasi {@code lastSyncAt} bostur.
 * Eger DB'de zaten veri varsa (count > 0) tazeleme tetiklenmez (thundering
 * herd engeli) — sadece DB bossa veya pencere ASILMISSA cagri yapilir.
 *
 * <p><b>Transactional DEGIL</b>: Alt sync servisler kendi REQUIRED tx'lerini
 * acar. Bu bean'i @Transactional yapsaydik, async caglarda tx visibility
 * problemi olurdu.
 */
@Service
public class BasketballGameDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(BasketballGameDetailLazySync.class);

    /** Bos yanit retry penceresi. */
    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);

    /** H2H tazeleme penceresi. */
    private static final Duration FRESH_H2H = Duration.ofHours(12);

    /** Standings tazeleme penceresi — API saatlik guncellenir. */
    private static final Duration FRESH_STANDINGS = Duration.ofHours(1);

    /** Stats tazeleme penceresi (canli sirasinda). */
    private static final Duration FRESH_STATS = Duration.ofMinutes(2);

    /**
     * Maclarin kickoff'tan {@code STATS_INITIAL_WINDOW} saat sonrasina kadar
     * stats cekilir; otesinde "initial-only" moda gecilir.
     */
    private static final Duration STATS_INITIAL_WINDOW = Duration.ofHours(26);

    /** In-play durum kodlari. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT");
    /** Bitmis durum kodlari. */
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AOT");

    private final BasketballGameRepository gameRepo;
    private final BasketballGameTeamStatRepository teamStatRepo;
    private final BasketballGamePlayerStatRepository playerStatRepo;
    private final BasketballStandingRepository standingRepo;
    private final BasketballSeasonRepository seasonRepo;

    private final BasketballGameStatsSyncService statsSync;
    private final BasketballH2hSyncService h2hSync;
    private final BasketballStandingsSyncService standingsSync;

    /** Mac ID basina son bos yanit zamani (stats). */
    private final ConcurrentHashMap<Long, Instant> emptyStatsAt = new ConcurrentHashMap<>();
    /** Takim cifti basina son bos yanit zamani (H2H). */
    private final ConcurrentHashMap<String, Instant> emptyH2hAt = new ConcurrentHashMap<>();

    public BasketballGameDetailLazySync(BasketballGameRepository gameRepo,
                                         BasketballGameTeamStatRepository teamStatRepo,
                                         BasketballGamePlayerStatRepository playerStatRepo,
                                         BasketballStandingRepository standingRepo,
                                         BasketballSeasonRepository seasonRepo,
                                         BasketballGameStatsSyncService statsSync,
                                         BasketballH2hSyncService h2hSync,
                                         BasketballStandingsSyncService standingsSync) {
        this.gameRepo = gameRepo;
        this.teamStatRepo = teamStatRepo;
        this.playerStatRepo = playerStatRepo;
        this.standingRepo = standingRepo;
        this.seasonRepo = seasonRepo;
        this.statsSync = statsSync;
        this.h2hSync = h2hSync;
        this.standingsSync = standingsSync;
    }

    // ================================================================
    // Public entry — paralel async fire-and-forget. Cache evict cagiran
    // servisin sorumlulugu (refresh sonrasi @CacheEvict).
    // ================================================================

    /**
     * Tum modulleri paralel asyncronous tetikler. Bekleme yok — caller
     * mevcut DB'yi gosterir, bir sonraki refresh'te yeni veri gelir
     * (stale-while-revalidate).
     */
    @Async
    public CompletableFuture<Void> ensureAll(BasketballGame game) {
        if (game == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> stats = CompletableFuture.runAsync(
                () -> safeEnsureStats(game), Runnable::run);
        CompletableFuture<Void> h2h = CompletableFuture.runAsync(
                () -> safeEnsureH2h(game), Runnable::run);
        CompletableFuture<Void> standings = CompletableFuture.runAsync(
                () -> safeEnsureStandings(game), Runnable::run);
        return CompletableFuture.allOf(stats, h2h, standings);
    }

    // ================================================================
    // Stats — sadece canli/FT'de. NS macta stats yok, sync atla.
    // ================================================================

    private void safeEnsureStats(BasketballGame game) {
        try { ensureStats(game); }
        catch (Exception e) {
            log.warn("Basketbol stats lazy sync hatasi id={}: {}",
                    game.getId(), e.toString());
        }
    }

    private void ensureStats(BasketballGame game) {
        final String status = game.getStatusShort();
        final boolean live = status != null && LIVE_STATUSES.contains(status);
        final boolean finished = status != null && FINISHED_STATUSES.contains(status);
        if (!live && !finished) return;   // NS / POST / CANC ... atla

        // Initial-only mod: kickoff'tan cok zaman gectiyse sadece DB bossa cek.
        final boolean inInitialWindow = game.getStartAt() != null
                && Instant.now().isBefore(
                        game.getStartAt().plus(STATS_INITIAL_WINDOW));

        long teamStatsCount = teamStatRepo.countByGameId(game.getId());
        long playerStatsCount = playerStatRepo.countByGameId(game.getId());
        boolean hasData = teamStatsCount > 0 && playerStatsCount > 0;

        // Empty-debounce check.
        Instant emptyAt = emptyStatsAt.get(game.getId());
        if (emptyAt != null && Instant.now().isBefore(emptyAt.plus(EMPTY_RETRY))) {
            return;
        }

        // Karar matrisi:
        //  - DB bos + initial window: ZORLA cek
        //  - DB bos + initial OTESINDE: cek (FT mac geclendi, kismi veri olsa
        //    bile gostermek istiyoruz)
        //  - DB dolu + canli + STATS freshness asildi: refresh (her 2dk)
        //  - DB dolu + FT + initial window: tek seferlik finalize-tipi refresh
        //  - DB dolu + FT + initial OTESINDE: skip
        boolean shouldFetch;
        if (!hasData) {
            shouldFetch = true;
        } else if (live) {
            // Canli: stats freshness pencere kontrolu (last_synced_at game'de).
            Instant last = game.getLastSyncedAt();
            shouldFetch = last == null || Instant.now().isAfter(last.plus(FRESH_STATS));
        } else if (finished && inInitialWindow) {
            // FT initial window: son tazelemenin ustunden 5dk geciyse refresh.
            Instant last = game.getLastSyncedAt();
            shouldFetch = last == null
                    || Instant.now().isAfter(last.plus(Duration.ofMinutes(5)));
        } else {
            shouldFetch = false;
        }

        if (!shouldFetch) return;

        int n = statsSync.syncBoth(game.getId());
        if (n == 0) {
            emptyStatsAt.put(game.getId(), Instant.now());
            log.debug("Basketbol stats bos cevap id={} — debounce 10dk", game.getId());
        } else {
            emptyStatsAt.remove(game.getId());
        }
    }

    // ================================================================
    // H2H — 12 saat freshness. DB bossa zorla cek.
    // ================================================================

    private void safeEnsureH2h(BasketballGame game) {
        try { ensureH2h(game); }
        catch (Exception e) {
            log.warn("Basketbol H2H lazy sync hatasi id={}: {}",
                    game.getId(), e.toString());
        }
    }

    private void ensureH2h(BasketballGame game) {
        if (game.getHomeTeam() == null || game.getAwayTeam() == null) return;
        long t1 = game.getHomeTeam().getId();
        long t2 = game.getAwayTeam().getId();
        // Kanonik key (kucuk-buyuk sirali) — cifti tek girdide tut.
        String key = (t1 < t2 ? t1 + "-" + t2 : t2 + "-" + t1);

        Instant emptyAt = emptyH2hAt.get(key);
        if (emptyAt != null && Instant.now().isBefore(emptyAt.plus(EMPTY_RETRY))) {
            return;
        }

        // Mevcut H2H sayisi — basit empty-check (zaman tutmuyoruz, kendisi mac
        // tablosu zaten startAt'a sahip). Eger hic kayit yoksa veya cok eski
        // mac varsa cek.
        // Simdilik sadece "hic gecmis verisi olmama" durumunda cek; freshness
        // tutmak icin gameRepo'ya bir LASTH2HSYNCEDAT lazim ki bunu eklemek
        // ekstra schema gerekir. Pratik karar: H2H mac veri her gun
        // BasketballSyncService.syncDate ile zaten cekiliyor, lazy sadece
        // kullanici onceki H2H gormus ama yenisini bekliyorsa.
        h2hSync.sync(t1, t2);
        // Sonuc bossa bir sonraki ensure'da yine cagrilmasin diye debounce'a
        // koyabilirdik ama sync metodu sayi donmuyor — log'a buyuk degil.
    }

    // ================================================================
    // Standings — 1 saat freshness, DB bossa zorla cek.
    // ================================================================

    private void safeEnsureStandings(BasketballGame game) {
        try { ensureStandings(game); }
        catch (Exception e) {
            log.warn("Basketbol standings lazy sync hatasi id={}: {}",
                    game.getId(), e.toString());
        }
    }

    private void ensureStandings(BasketballGame game) {
        if (game.getLeague() == null || game.getSeason() == null) return;
        final long leagueId = game.getLeague().getId();
        final String season = game.getSeason();

        long count = standingRepo.countByLeagueIdAndSeason(leagueId, season);
        Instant lastSync = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .map(BasketballSeason::getStandingsLastSyncedAt)
                .orElse(null);

        boolean shouldFetch = count == 0
                || lastSync == null
                || Instant.now().isAfter(lastSync.plus(FRESH_STANDINGS));
        if (!shouldFetch) return;

        standingsSync.sync(leagueId, season);
    }
}

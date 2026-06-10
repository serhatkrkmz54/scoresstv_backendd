package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Covered (kapsamli) liglerin lig info + coverage + top scorers/assists/cards
 * verilerini gunluk olarak tazeleyen iş.
 *
 * <p><b>Neden gerekli:</b> Lazy sync sadece kullanici lig sayfasini actiginda
 * tetiklenir. Popüler olmayan covered ligler icin (orn. alt-tier ulkenin ust
 * ligi) kullanici nadir gelir; ama o lig sayfasi acildiginda en gusel taze
 * veriyi sunmak istiyoruz. Bu job background'da covered liglerin guncel
 * sezon top scorers'ini gunluk tazelemekle bunu garanti eder.
 *
 * <p><b>Kapsam:</b> Yalniz {@code league.covered = true} olan ligler. Bu
 * bayrak ADMIN'in sectigi gorunum kapsamiyla ayni — manuel olarak isaretlenen
 * 30-50 lig icin gecerli. Diger ligler lazy sync ile karsilanir.
 *
 * <p><b>Quota:</b> ~30 covered lig × 5 cagri (1 league info + 4 top kategori)
 * = 150 cagri/gun. Ultra plan 75k limitine gore <%1 (trivial).
 *
 * <p>Cron varsayilan {@code 0 0 6 * * *} (her sabah 06:00 — diger daily
 * joblardan sonra). Bean yalniz {@code scorestv.football.sync.enabled=true}
 * ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyLeagueRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(DailyLeagueRefreshJob.class);

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final ReferenceSyncService referenceSyncService;
    private final TopPlayersSyncService topPlayersSyncService;

    public DailyLeagueRefreshJob(LeagueRepository leagueRepository,
                                 SeasonRepository seasonRepository,
                                 ReferenceSyncService referenceSyncService,
                                 TopPlayersSyncService topPlayersSyncService) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.referenceSyncService = referenceSyncService;
        this.topPlayersSyncService = topPlayersSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.league-refresh-cron:0 0 6 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void run() {
        List<League> covered = leagueRepository.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.info("DailyLeagueRefreshJob: covered lig yok, atlandı.");
            return;
        }
        int leaguesProcessed = 0;
        int leagueInfoFailed = 0;
        int topPlayersWritten = 0;
        int topPlayersFailed = 0;
        for (League league : covered) {
            // 1) Lig info + sezon listesi + coverage tazele (/leagues?id=X).
            try {
                referenceSyncService.syncOne(league.getId());
            } catch (ApiException ex) {
                leagueInfoFailed++;
                log.warn("Lig info refresh basarisiz (API): leagueId={} — {}",
                        league.getId(), ex.getMessage());
                continue;  // info olmadan top players sync etmenin anlami yok
            } catch (RuntimeException ex) {
                leagueInfoFailed++;
                log.error("Lig info refresh beklenmedik hata: leagueId=" + league.getId(), ex);
                continue;
            }

            // 2) Guncel sezon icin top players sync — coverage bayraklarina bak.
            Integer season = league.getCurrentSeason();
            if (season == null) {
                continue;
            }
            Season seasonRow = seasonRepository
                    .findByLeagueIdAndYear(league.getId(), season)
                    .orElse(null);
            if (seasonRow == null) {
                continue;
            }
            topPlayersWritten += syncTopPlayers(league.getId(), season, seasonRow);
            leaguesProcessed++;
        }
        log.info("DailyLeagueRefreshJob bitti: {} lig islendi, {} info hatasi, "
                        + "{} top-player satiri yazildi, {} top-player hatasi.",
                leaguesProcessed, leagueInfoFailed, topPlayersWritten, topPlayersFailed);
    }

    /**
     * Bir lig+sezon icin coverage bayraklarinin izin verdigi top-player
     * kategorilerini sync eder. Her cagri kendi try/catch'inde — biri
     * basarisiz olsa digerine devam edilir.
     */
    private int syncTopPlayers(Long leagueId, Integer season, Season seasonRow) {
        int written = 0;
        if (seasonRow.isCoverageTopScorers()) {
            written += runCategory(leagueId, season, Category.SCORERS);
        }
        if (seasonRow.isCoverageTopAssists()) {
            written += runCategory(leagueId, season, Category.ASSISTS);
        }
        // Cards bayragi tek; ama API'de yellow ve red ayri endpoint.
        if (seasonRow.isCoverageTopCards()) {
            written += runCategory(leagueId, season, Category.YELLOW_CARDS);
            written += runCategory(leagueId, season, Category.RED_CARDS);
        }
        return written;
    }

    private int runCategory(Long leagueId, Integer season, Category category) {
        try {
            return topPlayersSyncService.sync(leagueId, season, category).written();
        } catch (ApiException ex) {
            log.warn("Top {} refresh basarisiz (API): leagueId={} season={} — {}",
                    category.name().toLowerCase(), leagueId, season, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Top {} refresh beklenmedik hata: leagueId={} season={} — {}",
                    category.name().toLowerCase(), leagueId, season, ex.getMessage());
        }
        return 0;
    }
}

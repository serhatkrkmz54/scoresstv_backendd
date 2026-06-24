package com.scorestv.volleyball.detail;

import com.scorestv.volleyball.VolleyballH2hSyncService;
import com.scorestv.volleyball.VolleyballStandingsSyncService;
import com.scorestv.volleyball.VolleyballTeamStatisticsSyncService;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballSeason;
import com.scorestv.volleyball.domain.VolleyballSeasonRepository;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voleybol mac detay endpoint'i ihtiyac duyulan yan modulleri (h2h, standings,
 * sezon takim istatistikleri) tembel olarak API'den ceker. LEANER — voleybolda
 * mac-bazli stats yoktur.
 *
 * <p><b>Transactional DEGIL</b>: Alt sync servisler kendi REQUIRED tx'lerini acar.
 */
@Service
public class VolleyballGameDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(VolleyballGameDetailLazySync.class);

    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);
    private static final Duration FRESH_STANDINGS = Duration.ofHours(1);

    private final VolleyballStandingRepository standingRepo;
    private final VolleyballSeasonRepository seasonRepo;

    private final VolleyballH2hSyncService h2hSync;
    private final VolleyballStandingsSyncService standingsSync;
    private final VolleyballTeamStatisticsSyncService teamStatsSync;

    private final ConcurrentHashMap<String, Instant> emptyH2hAt = new ConcurrentHashMap<>();

    public VolleyballGameDetailLazySync(VolleyballStandingRepository standingRepo,
                                        VolleyballSeasonRepository seasonRepo,
                                        VolleyballH2hSyncService h2hSync,
                                        VolleyballStandingsSyncService standingsSync,
                                        VolleyballTeamStatisticsSyncService teamStatsSync) {
        this.standingRepo = standingRepo;
        this.seasonRepo = seasonRepo;
        this.h2hSync = h2hSync;
        this.standingsSync = standingsSync;
        this.teamStatsSync = teamStatsSync;
    }

    /** Tum modulleri paralel asyncronous tetikler. Bekleme yok (stale-while-revalidate). */
    @Async
    public CompletableFuture<Void> ensureAll(VolleyballGame game) {
        if (game == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> h2h = CompletableFuture.runAsync(
                () -> safeEnsureH2h(game), Runnable::run);
        CompletableFuture<Void> standings = CompletableFuture.runAsync(
                () -> safeEnsureStandings(game), Runnable::run);
        CompletableFuture<Void> stats = CompletableFuture.runAsync(
                () -> safeEnsureTeamStats(game), Runnable::run);
        return CompletableFuture.allOf(h2h, standings, stats);
    }

    private void safeEnsureH2h(VolleyballGame game) {
        try { ensureH2h(game); }
        catch (Exception e) {
            log.warn("Voleybol H2H lazy sync hatasi id={}: {}", game.getId(), e.toString());
        }
    }

    private void ensureH2h(VolleyballGame game) {
        if (game.getHomeTeam() == null || game.getAwayTeam() == null) return;
        long t1 = game.getHomeTeam().getId();
        long t2 = game.getAwayTeam().getId();
        String key = (t1 < t2 ? t1 + "-" + t2 : t2 + "-" + t1);

        Instant emptyAt = emptyH2hAt.get(key);
        if (emptyAt != null && Instant.now().isBefore(emptyAt.plus(EMPTY_RETRY))) {
            return;
        }
        int n = h2hSync.sync(t1, t2);
        if (n == 0) emptyH2hAt.put(key, Instant.now());
        else emptyH2hAt.remove(key);
    }

    private void safeEnsureStandings(VolleyballGame game) {
        try { ensureStandings(game); }
        catch (Exception e) {
            log.warn("Voleybol standings lazy sync hatasi id={}: {}", game.getId(), e.toString());
        }
    }

    private void ensureStandings(VolleyballGame game) {
        if (game.getLeague() == null || game.getSeason() == null) return;
        final long leagueId = game.getLeague().getId();
        final String season = game.getSeason();

        long count = standingRepo.countByLeagueIdAndSeason(leagueId, season);
        Instant lastSync = seasonRepo.findByLeagueIdAndSeason(leagueId, season)
                .map(VolleyballSeason::getStandingsLastSyncedAt)
                .orElse(null);

        boolean shouldFetch = count == 0
                || lastSync == null
                || Instant.now().isAfter(lastSync.plus(FRESH_STANDINGS));
        if (!shouldFetch) return;

        standingsSync.sync(leagueId, season);
    }

    private void safeEnsureTeamStats(VolleyballGame game) {
        try { ensureTeamStats(game); }
        catch (Exception e) {
            log.warn("Voleybol team stats lazy sync hatasi id={}: {}", game.getId(), e.toString());
        }
    }

    private void ensureTeamStats(VolleyballGame game) {
        if (game.getLeague() == null || game.getSeason() == null) return;
        final long leagueId = game.getLeague().getId();
        final String season = game.getSeason();
        if (game.getHomeTeam() != null) {
            teamStatsSync.sync(game.getHomeTeam().getId(), leagueId, season, false);
        }
        if (game.getAwayTeam() != null) {
            teamStatsSync.sync(game.getAwayTeam().getId(), leagueId, season, false);
        }
    }
}

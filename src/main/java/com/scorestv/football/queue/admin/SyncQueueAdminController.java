package com.scorestv.football.queue.admin;

import com.scorestv.football.ApiQuotaTracker;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.queue.SyncJobRepository;
import com.scorestv.football.queue.SyncJobStatus;
import com.scorestv.football.queue.SyncJobType;
import com.scorestv.football.queue.SyncQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sync queue admin endpoint koleksiyonu — bulk enqueue + monitoring.
 *
 * <p>Tipik kullanim sirasi:
 * <ol>
 *   <li>{@code POST /admin/sync-queue/enqueue-all-squads} — DB'deki tum
 *       takimlar icin SQUAD_SYNC job'lari ekler (5000 takim → 5000 job).</li>
 *   <li>{@code GET /admin/sync-queue/stats} — saat bazli ilerleme</li>
 *   <li>2sn aralikla worker isleyince ~3 saat sonra biter</li>
 *   <li>DB'de ~125k oyuncu yer alir</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/sync-queue")
@PreAuthorize("hasRole('ADMIN')")
public class SyncQueueAdminController {

    private static final Logger log = LoggerFactory.getLogger(SyncQueueAdminController.class);

    private final SyncQueueService queueService;
    private final SyncJobRepository repository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final ApiQuotaTracker quotaTracker;

    public SyncQueueAdminController(SyncQueueService queueService,
                                    SyncJobRepository repository,
                                    TeamRepository teamRepository,
                                    LeagueRepository leagueRepository,
                                    ApiQuotaTracker quotaTracker) {
        this.queueService = queueService;
        this.repository = repository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.quotaTracker = quotaTracker;
    }

    /** Mevcut API kota durumu — admin dashboard icin. */
    @GetMapping("/quota")
    public ApiQuotaTracker.QuotaSnapshot quotaSnapshot() {
        return quotaTracker.snapshot();
    }

    // ============================================================
    // Bulk enqueue
    // ============================================================

    /**
     * DB'deki TUM takimlar icin TEAM_SQUAD_SYNC job'lari ekler.
     * 5000 takim × 1 job → 5000 satir. Worker 2sn/is ile ~3 saat surer.
     *
     * @param season hangi sezon icin — null verilirse takimin son fixture
     *               sezonu kullanilir (servis tarafinda) ya da current yil
     */
    @PostMapping("/enqueue-all-squads")
    @Transactional
    public EnqueueResult enqueueAllSquads(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "7") int priority) {
        Integer effectiveSeason = season != null ? season : currentYear();
        int added = 0;
        for (Long teamId : teamRepository.findAll().stream()
                .map(t -> t.getId()).toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamId", teamId);
            payload.put("season", effectiveSeason);
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_SQUAD_SYNC,
                    payload, priority)) {
                added++;
            }
        }
        log.info("Bulk squad enqueue: {} job eklendi (season={}, priority={})",
                added, effectiveSeason, priority);
        return new EnqueueResult(SyncJobType.TEAM_SQUAD_SYNC.name(), added);
    }

    /**
     * COVERED takimlar icin player season stats job'lari. Daha az is
     * (~100 takim) ama pahali (her job paginated).
     */
    @PostMapping("/enqueue-covered-team-player-stats")
    @Transactional
    public EnqueueResult enqueueCoveredTeamPlayerStats(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "5") int priority) {
        Integer effectiveSeason = season != null ? season : currentYear();
        int added = 0;
        List<Long> ids = teamRepository.findAll().stream()
                .filter(t -> t.isCovered())
                .map(t -> t.getId())
                .toList();
        for (Long teamId : ids) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamId", teamId);
            payload.put("season", effectiveSeason);
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_PLAYER_STATS_SYNC,
                    payload, priority)) added++;
        }
        return new EnqueueResult(SyncJobType.TEAM_PLAYER_STATS_SYNC.name(), added);
    }

    /** Tum takimlar icin TEAM_TRANSFERS_SYNC. */
    @PostMapping("/enqueue-all-team-transfers")
    @Transactional
    public EnqueueResult enqueueAllTeamTransfers(
            @RequestParam(defaultValue = "7") int priority) {
        int added = 0;
        for (Long teamId : teamRepository.findAll().stream()
                .map(t -> t.getId()).toList()) {
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_TRANSFERS_SYNC,
                    Map.of("teamId", teamId), priority)) added++;
        }
        return new EnqueueResult(SyncJobType.TEAM_TRANSFERS_SYNC.name(), added);
    }

    /** Tum covered ligler icin standings sync. */
    @PostMapping("/enqueue-covered-league-standings")
    @Transactional
    public EnqueueResult enqueueCoveredLeagueStandings(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "3") int priority) {
        Integer effectiveSeason = season != null ? season : currentYear();
        int added = 0;
        for (Long leagueId : leagueRepository.findByCoveredTrue().stream()
                .map(l -> l.getId()).toList()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("leagueId", leagueId);
            payload.put("season", effectiveSeason);
            if (queueService.enqueueIfAbsent(SyncJobType.LEAGUE_STANDINGS_SYNC,
                    payload, priority)) added++;
        }
        return new EnqueueResult(SyncJobType.LEAGUE_STANDINGS_SYNC.name(), added);
    }

    // ============================================================
    // Monitoring + bakim
    // ============================================================

    /** Genel istatistik — pending/in-progress/completed/failed sayim. */
    @GetMapping("/stats")
    public StatsResult stats() {
        Map<SyncJobStatus, Long> byStatus = new EnumMap<>(SyncJobStatus.class);
        for (SyncJobStatus s : SyncJobStatus.values()) {
            byStatus.put(s, repository.countByStatus(s));
        }
        Map<String, Long> pendingByType = new HashMap<>();
        for (SyncJobType t : SyncJobType.values()) {
            long n = repository.countByTypeAndStatus(t, SyncJobStatus.PENDING);
            if (n > 0) pendingByType.put(t.name(), n);
        }
        // ETA — sadece PENDING'ler icin, 2sn/is hizinda
        long pending = byStatus.getOrDefault(SyncJobStatus.PENDING, 0L);
        long etaSeconds = pending * 2;
        return new StatsResult(byStatus, pendingByType, etaSeconds);
    }

    /** Tum FAILED'leri PENDING'e cevirir (yeniden denemek icin). */
    @PostMapping("/retry-failed")
    @Transactional
    public RetryResult retryFailed() {
        int n = repository.retryAllFailed();
        log.info("Retry-failed: {} job PENDING'e alindi", n);
        return new RetryResult(n);
    }

    /** Eski COMPLETED/FAILED satirlari siler (bakim). Default: 7 gunden eskiler. */
    @DeleteMapping("/cleanup")
    @Transactional
    public CleanupResult cleanup(@RequestParam(defaultValue = "7") int olderThanDays) {
        Instant before = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);
        int n = repository.deleteOlderThan(before);
        log.info("Sync queue cleanup: {} eski kayit silindi (older than {}d)",
                n, olderThanDays);
        return new CleanupResult(n);
    }

    private static int currentYear() {
        return java.time.LocalDate.now().getYear();
    }

    // ============================================================
    // Response records
    // ============================================================

    public record EnqueueResult(String jobType, int enqueued) {}

    public record StatsResult(
            Map<SyncJobStatus, Long> byStatus,
            Map<String, Long> pendingByType,
            long etaSeconds) {}

    public record RetryResult(int retriedCount) {}

    public record CleanupResult(int deletedCount) {}
}

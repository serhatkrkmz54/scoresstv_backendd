package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yeni biten voleybol maclari icin bir kez "son tazeleme" yapan is.
 *
 * <p>Voleybolda mac-bazli oyuncu/takim istatistigi YOK; bu yuzden finalize
 * sadece macin kendisini ({@code /games?id=X}) yeniden ceker (API son set
 * skorlarinda kucuk duzeltmeler yayinlayabilir) ve standings'i tazeler.
 *
 * <p>Idempotency: bellekte {@code finalizedIds} setiyle ayni mac iki kere
 * finalize edilmez. Yalniz {@code scorestv.volleyball.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballFinishedGameFinalSyncJob {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballFinishedGameFinalSyncJob.class);

    private static final Duration MIN_AGE = Duration.ofMinutes(30);
    private static final Duration MAX_AGE = Duration.ofHours(24);
    private static final long FIXED_DELAY_MS = 15 * 60_000L;

    private final VolleyballGameRepository gameRepo;
    private final VolleyballApiClient client;
    private final VolleyballSyncService syncService;
    private final VolleyballStandingsSyncService standingsSyncService;

    private final Set<Long> finalizedIds = ConcurrentHashMap.newKeySet();

    public VolleyballFinishedGameFinalSyncJob(VolleyballGameRepository gameRepo,
                                              VolleyballApiClient client,
                                              VolleyballSyncService syncService,
                                              VolleyballStandingsSyncService standingsSyncService) {
        this.gameRepo = gameRepo;
        this.client = client;
        this.syncService = syncService;
        this.standingsSyncService = standingsSyncService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = 60_000L)
    @SchedulerLock(name = "volleyballFinishedGameFinalSync", lockAtMostFor = "PT30M")
    public void run() {
        Instant now = Instant.now();
        Instant from = now.minus(MAX_AGE);
        Instant until = now.minus(MIN_AGE);

        List<VolleyballGame> games;
        try {
            games = gameRepo.findRecentlyFinished(from, until);
        } catch (Exception e) {
            log.warn("Voleybol finalize: biten mac sorgusu hata: {}", e.toString());
            return;
        }

        int processed = 0;
        for (VolleyballGame g : games) {
            if (!finalizedIds.add(g.getId())) continue;
            try {
                // Maci yeniden cek (son set duzeltmeleri) — notify=false.
                var fresh = client.fetchGameById(g.getId());
                if (!fresh.isEmpty()) {
                    syncService.upsertAll(fresh, false);
                }
                // Standings tazele.
                if (g.getLeague() != null && g.getSeason() != null) {
                    standingsSyncService.sync(g.getLeague().getId(), g.getSeason());
                }
                processed++;
            } catch (Exception e) {
                log.warn("Voleybol finalize hata id={}: {}", g.getId(), e.toString());
                finalizedIds.remove(g.getId());
            }
        }
        if (processed > 0) {
            log.info("Voleybol finalize sync: {} mac finalize edildi", processed);
        }
    }
}

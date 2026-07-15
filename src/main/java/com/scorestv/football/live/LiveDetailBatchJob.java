package com.scorestv.football.live;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Canlı detay BATCH job'u — {@code live-bundle-enabled=true} iken her 15 sn
 * {@link LiveDetailBatchService}'i çalıştırır (tek {@code /fixtures?ids=} ile
 * tüm canlı maçların events+statistics+players'ı).
 *
 * <p>Bayrak KAPALI iken hiçbir şey yapmaz — eski per-fixture LiveEvents/
 * LiveStatistics/LivePlayerStats joblari çalışmaya devam eder. Böylece kademeli,
 * risksiz geçiş: bayrağı aç → bu job devralır, eskiler kendini devre dışı bırakır.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class LiveDetailBatchJob {

    private static final Logger log = LoggerFactory.getLogger(LiveDetailBatchJob.class);

    private final LiveDetailBatchService service;
    private final SyncRateLimiter rateLimiter;

    public LiveDetailBatchJob(LiveDetailBatchService service, SyncRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.live-bundle-interval-seconds:15}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "liveDetailBundle", lockAtMostFor = "PT2M")
    public void run() {
        if (!rateLimiter.isLiveBundleEnabled()) {
            return; // bayrak kapalı — eski per-fixture joblar devrede
        }
        try {
            int n = service.run();
            if (n > 0) {
                log.info("Canlı detay batch: {} maç tek-çağrı bundle ile güncellendi.", n);
            }
        } catch (RuntimeException ex) {
            log.error("Canlı detay batch job hata", ex);
        }
    }
}

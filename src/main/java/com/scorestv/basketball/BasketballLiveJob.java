package com.scorestv.basketball;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Basketbol canlı skor sync — API-Basketball'da {@code live=all} yok; bugünün
 * maçlarını ({@code /games?date=bugün}) {@code live-interval-seconds} aralığıyla
 * çekerek in-play skorları tazeler (API her 15 sn günceller).
 *
 * <p>Yalnızca {@code scorestv.basketball.live-enabled=true} ise bean oluşur
 * (enabled ayrıca true olmalı ki client/sync çalışsın).
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.live-enabled", havingValue = "true")
public class BasketballLiveJob {

    private final BasketballSyncService sync;
    private final BasketballLiveBroadcaster broadcaster;

    public BasketballLiveJob(BasketballSyncService sync,
                             BasketballLiveBroadcaster broadcaster) {
        this.sync = sync;
        this.broadcaster = broadcaster;
    }

    @Scheduled(
            fixedDelayString = "#{${scorestv.basketball.live-interval-seconds:20} * 1000}",
            initialDelay = 15_000)
    @SchedulerLock(name = "basketballLive", lockAtMostFor = "PT2M")
    public void run() {
        sync.syncLive();
        // Sync sonrası taze canlı skorları WS ile mobile'a it.
        broadcaster.broadcastLive();
    }
}

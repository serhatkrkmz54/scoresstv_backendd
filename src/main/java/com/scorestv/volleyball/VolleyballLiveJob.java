package com.scorestv.volleyball;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Voleybol canli skor sync — API-Volleyball'da {@code live=all} yok; bugunun
 * maclarini {@code live-interval-seconds} araligiyla cekerek in-play skorlari
 * tazeler. Yalnizca {@code scorestv.volleyball.live-enabled=true} ise bean olusur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.live-enabled", havingValue = "true")
public class VolleyballLiveJob {

    private final VolleyballSyncService sync;
    private final VolleyballLiveBroadcaster broadcaster;

    public VolleyballLiveJob(VolleyballSyncService sync,
                             VolleyballLiveBroadcaster broadcaster) {
        this.sync = sync;
        this.broadcaster = broadcaster;
    }

    @Scheduled(
            fixedDelayString = "#{${scorestv.volleyball.live-interval-seconds:20} * 1000}",
            initialDelay = 15_000)
    @SchedulerLock(name = "volleyballLive", lockAtMostFor = "PT2M")
    public void run() {
        sync.syncLive();
        broadcaster.broadcastLive();
    }
}

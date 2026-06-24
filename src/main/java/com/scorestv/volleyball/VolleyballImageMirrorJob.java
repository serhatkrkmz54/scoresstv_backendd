package com.scorestv.volleyball;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Voleybol logo/bayrak aynalama isi — yeni gelen (logoKey'i bos) takim/lig
 * gorsellerini saatlik olarak MinIO/CDN'e tasir. Yalnizca
 * {@code scorestv.volleyball.enabled=true} ise calisir.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballImageMirrorJob {

    private final VolleyballImageMirrorService mirror;

    public VolleyballImageMirrorJob(VolleyballImageMirrorService mirror) {
        this.mirror = mirror;
    }

    @Scheduled(
            cron = "${scorestv.volleyball.image-mirror-cron:0 15 * * * *}",
            zone = "${scorestv.volleyball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "volleyballImageMirror", lockAtMostFor = "PT15M")
    public void run() {
        mirror.mirrorAll();
    }
}

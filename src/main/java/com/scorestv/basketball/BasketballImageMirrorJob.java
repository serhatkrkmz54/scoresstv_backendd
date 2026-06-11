package com.scorestv.basketball;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Basketbol logo/bayrak aynalama işi — yeni gelen (logoKey'i boş) takım/lig
 * görsellerini saatlik olarak MinIO/CDN'e taşır. Football'daki ImageMirrorJob
 * ile aynı mantık. Yalnızca {@code scorestv.basketball.enabled=true} ise çalışır.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballImageMirrorJob {

    private final BasketballImageMirrorService mirror;

    public BasketballImageMirrorJob(BasketballImageMirrorService mirror) {
        this.mirror = mirror;
    }

    @Scheduled(
            cron = "${scorestv.basketball.image-mirror-cron:0 15 * * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    public void run() {
        mirror.mirrorAll();
    }
}

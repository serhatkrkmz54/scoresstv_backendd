package com.scorestv.basketball;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Basketbol referans seed işi — ülkeler + ligler (haftalık + açılışta).
 * Referans veri stabildir; sık çekmeye gerek yok. Yalnızca
 * {@code scorestv.basketball.enabled=true} ise bean oluşur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballReferenceJob {

    private final BasketballReferenceService ref;

    public BasketballReferenceJob(BasketballReferenceService ref) {
        this.ref = ref;
    }

    @Scheduled(
            cron = "${scorestv.basketball.reference-cron:0 0 3 * * 1}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    public void run() {
        ref.syncCountries();
        ref.syncLeagues();
    }
    // Açılış seed'i BasketballStartupRunner'da (games'ten önce sıralı çalışır).
}

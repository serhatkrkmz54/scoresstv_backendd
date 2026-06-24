package com.scorestv.volleyball;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Voleybol referans seed isi — ulkeler + ligler (haftalik + acilista).
 * Yalnizca {@code scorestv.volleyball.enabled=true} ise bean olusur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballReferenceJob {

    private final VolleyballReferenceService ref;

    public VolleyballReferenceJob(VolleyballReferenceService ref) {
        this.ref = ref;
    }

    @Scheduled(
            cron = "${scorestv.volleyball.reference-cron:0 0 3 * * 1}",
            zone = "${scorestv.volleyball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "volleyballReference", lockAtMostFor = "PT30M")
    public void run() {
        ref.syncCountries();
        ref.syncLeagues();
    }
}

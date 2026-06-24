package com.scorestv.volleyball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Acilis catch-up — referans (ulke+lig) seed'i ile fikstur pencere sync'ini
 * TEK async gorevde SIRAYLA calistirir (PK cakismasini onler).
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(VolleyballStartupRunner.class);

    private final VolleyballReferenceService reference;
    private final VolleyballSyncService sync;
    private final VolleyballTeamSyncService teamSync;
    private final VolleyballImageMirrorService imageMirror;

    public VolleyballStartupRunner(VolleyballReferenceService reference,
                                   VolleyballSyncService sync,
                                   VolleyballTeamSyncService teamSync,
                                   VolleyballImageMirrorService imageMirror) {
        this.reference = reference;
        this.sync = sync;
        this.teamSync = teamSync;
        this.imageMirror = imageMirror;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            reference.syncCountries();
            reference.syncLeagues();
        } catch (Exception e) {
            log.warn("Voleybol referans seed (startup) hata: {}", e.toString());
        }
        for (LocalDate d : sync.windowDates()) {
            try {
                sync.syncDate(d);
            } catch (Exception e) {
                log.warn("Voleybol pencere sync (startup) tarih hata {}: {}", d, e.toString());
            }
        }
        try {
            teamSync.syncAllCurrentSeasons();
        } catch (Exception e) {
            log.warn("Voleybol team sync (startup) hata: {}", e.toString());
        }
        try {
            imageMirror.mirrorAll();
        } catch (Exception e) {
            log.warn("Voleybol image mirror (startup) hata: {}", e.toString());
        }
        log.info("Voleybol acilis catch-up tamamlandi.");
    }
}

package com.scorestv.basketball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Açılış catch-up — referans (ülke+lig) seed'i ile fikstür pencere sync'ini
 * TEK async görevde SIRAYLA çalıştırır.
 *
 * <p>Önemli: önceki halde iki ayrı {@code @Async} {@code onStartup} eşzamanlı
 * çalışıyordu; ikisi de {@code basketball_leagues}'a aynı ligi eklemeye
 * çalışınca PK çakışması (duplicate key) oluyordu. Burada referans ÖNCE biter
 * (ligler DB'de olur), sonra games sync sadece update yapar → çakışma yok.
 * {@code @Async} olduğu için uygulama açılışını bloklamaz.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballStartupRunner {

    private static final Logger log = LoggerFactory.getLogger(BasketballStartupRunner.class);

    private final BasketballReferenceService reference;
    private final BasketballSyncService sync;
    private final BasketballTeamSyncService teamSync;
    private final BasketballImageMirrorService imageMirror;

    public BasketballStartupRunner(BasketballReferenceService reference,
                                   BasketballSyncService sync,
                                   BasketballTeamSyncService teamSync,
                                   BasketballImageMirrorService imageMirror) {
        this.reference = reference;
        this.sync = sync;
        this.teamSync = teamSync;
        this.imageMirror = imageMirror;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        // 1) Referans önce — ligler/ülkeler DB'ye otursun.
        try {
            reference.syncCountries();
            reference.syncLeagues();
        } catch (Exception e) {
            log.warn("Basketbol referans seed (startup) hata: {}", e.toString());
        }
        // 2) Sonra fikstür penceresi (±gün) — ligler var, sadece update + yeni maç.
        for (LocalDate d : sync.windowDates()) {
            try {
                sync.syncDate(d);
            } catch (Exception e) {
                log.warn("Basketbol pencere sync (startup) tarih hata {}: {}", d, e.toString());
            }
        }
        // 3) Takım kadrosu (lig+sezon junction) — onboarding "favori takım"
        // akışı için kritik. Reference'tan currentSeason'ları öğrendikten sonra
        // /teams çağrıları yapar (debounce: 12sa içinde sync'lenmiş atlar).
        try {
            teamSync.syncAllCurrentSeasons();
        } catch (Exception e) {
            log.warn("Basketbol team sync (startup) hata: {}", e.toString());
        }
        // 4) Logoları aynala (CDN'e taşı) — ilk açılışta görseller hazır olsun.
        try {
            imageMirror.mirrorAll();
        } catch (Exception e) {
            log.warn("Basketbol image mirror (startup) hata: {}", e.toString());
        }
        log.info("Basketbol açılış catch-up tamamlandı.");
    }
}

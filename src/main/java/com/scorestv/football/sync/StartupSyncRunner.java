package com.scorestv.football.sync;

import com.scorestv.football.FootballProperties;
import com.scorestv.football.image.ImageMirrorResult;
import com.scorestv.football.image.ImageMirrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Uygulama ilk açıldığında verileri <b>sırayla</b> çeker:
 *
 * <ol>
 *   <li>Ülkeler + ligler + sezonlar ({@code /countries}, {@code /leagues})</li>
 *   <li>Takımlar + stadyumlar ({@code /teams})</li>
 *   <li>Fikstür penceresi (bugün ±N gün)</li>
 *   <li>Görsel aynalama (logo/bayrak → MinIO)</li>
 * </ol>
 *
 * <p>Tek bir {@code @Async} iş olduğu için adımlar tek bir arka plan iş parçacığında
 * sırayla çalışır ve uygulama açılışını bloklamaz. Her adım "boşsa/eksikse çek"
 * mantığıyla çalışır; yeniden başlatmada dolu veriyi atlayıp API kotasını korur.
 */
@Component
public class StartupSyncRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSyncRunner.class);

    private final ReferenceSyncService referenceSyncService;
    private final TeamSyncService teamSyncService;
    private final FixtureSyncService fixtureSyncService;
    private final ImageMirrorService imageMirrorService;
    private final FootballProperties properties;

    public StartupSyncRunner(ReferenceSyncService referenceSyncService,
                             TeamSyncService teamSyncService,
                             FixtureSyncService fixtureSyncService,
                             ImageMirrorService imageMirrorService,
                             FootballProperties properties) {
        this.referenceSyncService = referenceSyncService;
        this.teamSyncService = teamSyncService;
        this.fixtureSyncService = fixtureSyncService;
        this.imageMirrorService = imageMirrorService;
        this.properties = properties;
    }

    /** Uygulama hazır olduğunda başlangıç senkronunu sırayla, arka planda çalıştırır. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.sync().enabled()) {
            log.info("Senkron kapalı (scorestv.football.sync.enabled=false); "
                    + "başlangıç senkronu atlandı.");
            return;
        }
        log.info("Başlangıç senkronu başladı — sıra: referans, takımlar, "
                + "fikstürler, görseller.");

        // Adım 1: Referans veri — ülkeler, ligler + sezonlar (boşsa).
        try {
            ReferenceSyncResult reference = referenceSyncService.syncIfEmpty();
            log.info("Başlangıç [1] referans senkronu tamamlandı: {}", reference);
        } catch (RuntimeException ex) {
            log.error("Başlangıç referans senkronu başarısız: {}", ex.getMessage());
        }

        // Adım 2: Takımlar + stadyumlar (hiç detaylanmamışsa).
        try {
            TeamSyncResult teams = teamSyncService.syncIfNeeded();
            log.info("Başlangıç [2] takım senkronu tamamlandı: {}", teams);
        } catch (RuntimeException ex) {
            log.error("Başlangıç takım senkronu başarısız: {}", ex.getMessage());
        }

        // Adım 3: Fikstür penceresi — eksik tarihler (boşsa).
        try {
            FixtureSyncResult fixtures = fixtureSyncService.syncMissingDates();
            log.info("Başlangıç [3] fikstür senkronu tamamlandı: {}", fixtures);
        } catch (RuntimeException ex) {
            log.error("Başlangıç fikstür senkronu başarısız: {}", ex.getMessage());
        }

        // Adım 4: Görsel aynalama — aynalanmamış logo/bayraklar (aynalanmışları atlar).
        try {
            ImageMirrorResult images = imageMirrorService.mirrorAll();
            log.info("Başlangıç [4] görsel aynalama tamamlandı: {}", images);
        } catch (RuntimeException ex) {
            log.error("Başlangıç görsel aynalama başarısız: {}", ex.getMessage());
        }

        log.info("Başlangıç senkronu bitti.");
    }
}

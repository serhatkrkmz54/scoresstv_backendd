package com.scorestv.football.image;

import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aynalanmamış logo/bayrak görsellerini periyodik olarak MinIO'ya aktaran
 * zamanlanmış iş. Her saat çalışır; yeni senkronlanan varlıkların görsellerini
 * yakalar. Aynalanmış görseller atlandığı için tekrar maliyeti yoktur.
 *
 * <p>İlk (toplu) aynalama {@code StartupSyncRunner} tarafından açılışta yapılır.
 * {@code scorestv.football.sync.enabled} bayrağına tabidir.
 */
@Component
public class ImageMirrorJob {

    private static final Logger log = LoggerFactory.getLogger(ImageMirrorJob.class);

    private final ImageMirrorService mirrorService;
    private final FootballProperties properties;

    public ImageMirrorJob(ImageMirrorService mirrorService, FootballProperties properties) {
        this.mirrorService = mirrorService;
        this.properties = properties;
    }

    /** Saat başı aynalanmamış görselleri işler. */
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "imageMirror", lockAtMostFor = "PT15M")
    public void onSchedule() {
        if (!properties.sync().enabled()) {
            return;
        }
        log.info("Zamanlanmış görsel aynalama tetikleniyor...");
        try {
            ImageMirrorResult result = mirrorService.mirrorAll();
            log.info("Zamanlanmış görsel aynalama tamamlandı: {}", result);
        } catch (RuntimeException ex) {
            log.error("Zamanlanmış görsel aynalama başarısız: {}", ex.getMessage());
        }
    }

    /**
     * Açılışta bir kez MEVCUT placeholder'ları otomatik temizler (admin gerekmez).
     * {@code placeholder-sha256} boşsa servis no-op döner. Asenkron — request
     * yolunu bloklamaz. İlk hash ayarlandıktan sonraki ilk açılışta backlog'u
     * temizler; sonraki açılışlarda placeholder kalmadığı için iş hafiftir.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupPurge() {
        if (!properties.sync().enabled()) {
            return;
        }
        log.info("Açılış placeholder temizliği tetikleniyor (otomatik)...");
        mirrorService.purgePlaceholdersAsync();
    }
}

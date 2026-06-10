package com.scorestv.football.sync;

import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fikstür penceresini her gün cron saatinde yeniden senkronlayan zamanlanmış iş.
 *
 * <p>Pencere "bugün"e göre yeniden hesaplandığından her gün bir gün ileri kayar
 * ve daima ±N gün tam kalır. Başlangıç senkronu ayrıca {@link StartupSyncRunner}
 * tarafından (sıralı olarak) yapılır.
 *
 * <p>{@code scorestv.football.sync.enabled} bayrağına tabidir; kapalıyken yalnızca
 * ADMIN manuel tetiklemesi iş görür.
 */
@Component
public class FixtureSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncJob.class);

    private final FixtureSyncService syncService;
    private final FootballProperties properties;

    public FixtureSyncJob(FixtureSyncService syncService, FootballProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    /** Cron saatinde (varsayılan 04:00) tüm pencereyi yeniden senkronlar. */
    @Scheduled(cron = "${scorestv.football.sync.window-cron}")
    public void onSchedule() {
        if (!properties.sync().enabled()) {
            return;
        }
        log.info("Zamanlanmış fikstür senkronu tetikleniyor (tam pencere)...");
        try {
            FixtureSyncResult result = syncService.syncWindow();
            log.info("Zamanlanmış fikstür senkronu tamamlandı: {}", result);
        } catch (RuntimeException ex) {
            log.error("Zamanlanmış fikstür senkronu başarısız: {}", ex.getMessage());
        }
    }
}

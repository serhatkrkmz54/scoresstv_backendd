package com.scorestv.football.sync;

import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Referans veriyi (ülke/lig/sezon) haftalık cron ile tazeleyen zamanlanmış iş.
 *
 * <p>Başlangıç senkronu ayrıca {@link StartupSyncRunner} tarafından (sıralı
 * olarak) yapılır.
 *
 * <p>{@code scorestv.football.sync.enabled} bayrağına tabidir; kapalıyken yalnızca
 * ADMIN manuel tetiklemesi iş görür.
 */
@Component
public class ReferenceSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSyncJob.class);

    private final ReferenceSyncService syncService;
    private final FootballProperties properties;

    public ReferenceSyncJob(ReferenceSyncService syncService, FootballProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    /** Haftalık cron ile tüm referans veriyi tazeler. */
    @Scheduled(cron = "${scorestv.football.sync.reference-cron}")
    public void onSchedule() {
        if (!properties.sync().enabled()) {
            return;
        }
        log.info("Zamanlanmış referans senkronu tetikleniyor (tam tarama)...");
        try {
            ReferenceSyncResult result = syncService.syncAll();
            log.info("Zamanlanmış referans senkronu tamamlandı: {}", result);
        } catch (RuntimeException ex) {
            log.error("Zamanlanmış referans senkronu başarısız: {}", ex.getMessage());
        }
    }
}

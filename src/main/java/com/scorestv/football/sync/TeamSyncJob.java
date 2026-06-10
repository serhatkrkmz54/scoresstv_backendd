package com.scorestv.football.sync;

import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Takım ve stadyum verisini haftalık olarak tazeleyen zamanlanmış iş.
 * Başlangıç senkronu ayrıca {@link StartupSyncRunner} tarafından yapılır.
 *
 * <p>{@code scorestv.football.sync.enabled} bayrağına tabidir.
 */
@Component
public class TeamSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TeamSyncJob.class);

    private final TeamSyncService syncService;
    private final FootballProperties properties;

    public TeamSyncJob(TeamSyncService syncService, FootballProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    /** Her Pazar 06:00'da tüm takım/stadyum verisini tazeler. */
    @Scheduled(cron = "0 0 6 * * SUN")
    public void onSchedule() {
        if (!properties.sync().enabled()) {
            return;
        }
        log.info("Zamanlanmış takım senkronu tetikleniyor...");
        try {
            TeamSyncResult result = syncService.syncAll();
            log.info("Zamanlanmış takım senkronu tamamlandı: {}", result);
        } catch (RuntimeException ex) {
            log.error("Zamanlanmış takım senkronu başarısız: {}", ex.getMessage());
        }
    }
}

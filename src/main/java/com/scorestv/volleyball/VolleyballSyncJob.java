package com.scorestv.volleyball;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Voleybol fikstur sync — basketbol ile AYNI mantik:
 * sik (today-cron) bugun+yarin; gunluk (window-cron) ±7 pencere tam tarama.
 * Yalnizca {@code scorestv.volleyball.enabled=true} ise bean olusur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.volleyball.enabled", havingValue = "true")
public class VolleyballSyncJob {

    private static final Logger log = LoggerFactory.getLogger(VolleyballSyncJob.class);

    private final VolleyballSyncService sync;
    private final VolleyballProperties props;

    public VolleyballSyncJob(VolleyballSyncService sync, VolleyballProperties props) {
        this.sync = sync;
        this.props = props;
    }

    /** Sik yenileme — bugun + yarin guncel kalsin. */
    @Scheduled(
            cron = "${scorestv.volleyball.today-cron:0 */30 * * * *}",
            zone = "${scorestv.volleyball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "volleyballSyncRunToday", lockAtMostFor = "PT30M")
    public void runToday() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        sync.syncDate(today);
        sync.syncDate(today.plusDays(1));
    }

    /** Tum ±gun pencere — gunluk tam tarama. */
    @Scheduled(
            cron = "${scorestv.volleyball.window-cron:0 0 4 * * *}",
            zone = "${scorestv.volleyball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "volleyballSyncRunWindow", lockAtMostFor = "PT30M")
    public void runWindow() {
        var dates = sync.windowDates();
        int total = 0;
        for (LocalDate d : dates) {
            try {
                total += sync.syncDate(d);
            } catch (Exception e) {
                log.warn("Voleybol pencere tarih hatasi {}: {}", d, e.toString());
            }
        }
        log.info("Voleybol pencere sync ({} tarih): toplam {} mac", dates.size(), total);
    }
}

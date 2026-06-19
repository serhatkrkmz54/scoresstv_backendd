package com.scorestv.basketball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Basketbol fikstür sync — football ile AYNI mantık:
 * <ul>
 *   <li>{@link #runToday()} — sık (varsayılan 30 dk): bugün + yarın taze tutulur.</li>
 *   <li>{@link #runWindow()} — günlük (04:00): ±7 günlük kayan pencere tamamen
 *       taranır; geçmiş maçlar finalize olur (FT skorları gelir), gelecek
 *       fikstür dolar. Pencere bugüne göre her gün kendiliğinden kayar.</li>
 *   <li>{@link #onStartup()} — açılışta bir kez pencereyi çeker (date strip ±7
 *       gün anında çalışsın).</li>
 * </ul>
 * Yalnızca {@code scorestv.basketball.enabled=true} ise bean oluşur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballSyncJob {

    private static final Logger log = LoggerFactory.getLogger(BasketballSyncJob.class);

    private final BasketballSyncService sync;
    private final BasketballProperties props;

    public BasketballSyncJob(BasketballSyncService sync, BasketballProperties props) {
        this.sync = sync;
        this.props = props;
    }

    /** Sık yenileme — bugün + yarın güncel kalsın. */
    @Scheduled(
            cron = "${scorestv.basketball.today-cron:0 */30 * * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "basketballSyncRunToday", lockAtMostFor = "PT30M")
    public void runToday() {
        LocalDate today = LocalDate.now(ZoneId.of(props.timezone()));
        sync.syncDate(today);
        sync.syncDate(today.plusDays(1));
    }

    /** Tüm ±gün pencere — günlük tam tarama (geçmiş finalize + gelecek fikstür). */
    @Scheduled(
            cron = "${scorestv.basketball.window-cron:0 0 4 * * *}",
            zone = "${scorestv.basketball.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "basketballSyncRunWindow", lockAtMostFor = "PT30M")
    public void runWindow() {
        var dates = sync.windowDates();
        int total = 0;
        for (LocalDate d : dates) {
            try {
                total += sync.syncDate(d); // dış çağrı → her tarih kendi transaction'ı
            } catch (Exception e) {
                log.warn("Basketbol pencere tarih hatası {}: {}", d, e.toString());
            }
        }
        log.info("Basketbol pencere sync ({} tarih): toplam {} maç", dates.size(), total);
    }
    // Açılış catch-up'ı BasketballStartupRunner'da (referans → games sıralı).
}

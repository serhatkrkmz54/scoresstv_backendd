package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Bugünün maçlarını saatlik tazeleyen iş. Tek bir
 * {@code GET /fixtures?date=BUGÜN} çağrısı yapıp upsert eder.
 *
 * <p>Üç senkron katmanı arasındaki boşluğu kapatır:
 * <ul>
 *   <li>{@link com.scorestv.football.live.LiveTickerJob} — 15 sn (yalnız
 *       aktif oynayan maçlar; alt liglerin maçları çoğunlukla buraya hiç
 *       düşmez)</li>
 *   <li>Bu iş — saatlik (BUGÜN için tüm durumlar: NS→FT, PST, CANC...)</li>
 *   <li>{@link FixtureSyncJob} — günlük 04:00 (±7 gün pencere — dün ve
 *       önceki günleri toparlar)</li>
 * </ul>
 *
 * <p>Bean yalnız {@code scorestv.football.sync.enabled=true} ise oluşur;
 * cron ifadesi {@code today-refresh-cron} ile, saat dilimi
 * {@code timezone} ile kontrol edilir.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class TodayRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(TodayRefreshJob.class);

    private final FixtureSyncService syncService;
    private final FootballProperties properties;

    public TodayRefreshJob(FixtureSyncService syncService, FootballProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
        log.info("TodayRefreshJob aktif: cron='{}' (zone={})",
                properties.sync().todayRefreshCron(), properties.sync().timezone());
    }

    @Scheduled(
            cron = "${scorestv.football.sync.today-refresh-cron:0 */15 * * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void run() {
        LocalDate today = LocalDate.now(ZoneId.of(properties.sync().timezone()));
        try {
            int upserted = syncService.syncDate(today);
            log.info("Bugün tazeleme bitti: {} — {} maç upsert edildi", today, upserted);
        } catch (ApiException ex) {
            // API kotası/erişim hatası — bir sonraki saatte tekrar denenir.
            log.warn("Bugün tazeleme başarısız (API): {} — {}", today, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Bugün tazeleme beklenmedik hata: " + today, ex);
        }
    }
}

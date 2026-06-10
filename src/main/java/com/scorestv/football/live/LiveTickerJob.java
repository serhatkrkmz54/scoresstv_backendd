package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.FootballProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Periyodik canlı tick'i {@link LiveTickerService}'e havale eden zamanlanmış iş.
 *
 * <p>Bean yalnız {@code scorestv.football.sync.live-enabled=true} ise oluşur.
 * Tick aralığı {@code scorestv.football.sync.live-interval-seconds} ile
 * ayarlanır (varsayılan 15 sn — API-Football'un önerdiği alt sınır).
 *
 * <p>{@code fixedDelay} kullanıyoruz: bir tick'in BİTİMİNDEN sonra N sn beklenir.
 * Böylece yavaş bir API çağrısı bir sonraki tick ile çakışmaz, kuyruk birikmez.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class LiveTickerJob {

    private static final Logger log = LoggerFactory.getLogger(LiveTickerJob.class);

    private final LiveTickerService tickerService;

    public LiveTickerJob(LiveTickerService tickerService, FootballProperties props) {
        this.tickerService = tickerService;
        log.info("LiveTickerJob aktif: her {} sn'de bir /fixtures?live=all yoklanır.",
                props.sync().liveIntervalSeconds());
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.live-interval-seconds:15}",
            timeUnit = TimeUnit.SECONDS)
    public void run() {
        try {
            tickerService.tick();
        } catch (ApiException ex) {
            // API kotası/erişim hatası — bir sonraki tick'te tekrar denenir.
            log.warn("Canlı tick başarısız (API): {}", ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Canlı tick beklenmedik hata", ex);
        }
    }
}

package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Canli basketbol maclarinda team + player istatistiklerini periyodik
 * tazeleyen is.
 *
 * <p>API-Basketball stats'i her 30-120 sn'de bir guncellenir. Detay sayfasi
 * acik olan kullanicilar zaten LazySync ile 2dk freshness kontrolu yapar,
 * ama anasayfa listesinden bagimsiz olarak canli maclarin stats'larinin
 * arkaplanda da tazelenmesi WebSocket push'lari ve "favori cihaz" senaryolari
 * icin onemli (kullanici detay acmasa bile dispatcher final dispatcher
 * gecislerinde gercek stats verisini gormeli).
 *
 * <p>Kotaya etkisi: ~5-10 canli mac × 2 endpoint × her 3dk = ~200 cagri/saat
 * × 4-6 saat (live pencere) = 800-1200 cagri/gun. Trivial.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballLiveStatsJob {

    private static final Logger log = LoggerFactory.getLogger(BasketballLiveStatsJob.class);

    /** Canli (in-play) durum kodlari. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT");

    /** Cron tetikleme: her 3 dakikada bir. */
    private static final long FIXED_DELAY_MS = 3 * 60_000L;

    private final BasketballGameRepository gameRepo;
    private final BasketballGameStatsSyncService statsSyncService;

    public BasketballLiveStatsJob(BasketballGameRepository gameRepo,
                                    BasketballGameStatsSyncService statsSyncService) {
        this.gameRepo = gameRepo;
        this.statsSyncService = statsSyncService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = 90_000L)
    public void run() {
        List<BasketballGame> live;
        try {
            live = gameRepo.findByStatusWithDetails(LIVE_STATUSES);
        } catch (Exception e) {
            log.warn("Basketbol live stats: canli sorgu hata: {}", e.toString());
            return;
        }
        if (live.isEmpty()) return;

        int processed = 0;
        for (BasketballGame g : live) {
            try {
                statsSyncService.syncBoth(g.getId());
                processed++;
            } catch (Exception e) {
                log.warn("Basketbol live stats hata id={}: {}", g.getId(), e.toString());
            }
        }
        log.debug("Basketbol live stats sync: {} canli mac tazelendi", processed);
    }
}

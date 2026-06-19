package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballSeason;
import com.scorestv.basketball.domain.BasketballSeasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Covered lig+sezonlar icin standings'i gunluk tazeleyen is.
 *
 * <p>API-Basketball standings'i her saat guncellenir — saatlik tetiklemek
 * gerekmez ama gunluk taze tutmak istiyoruz ki kullanici aciklanan
 * description (Conference Quarter-Finals vb.) en guncel halde gorsun.
 *
 * <p>Lazy sync standings'i 1sa freshness ile zaten tetikler — bu job sadece
 * kullanici hic detay/standings sayfasi acmamis covered ligler icin gunluk
 * birinin yapilmasini garantiler (cache build-up).
 *
 * <p>Kotaya etkisi: covered ligler × 1 cagri/gun. NBA + Euroleague + 5-10
 * TR/EU ligi = ~10-15 cagri/gun. Trivial.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class DailyBasketballStandingsJob {

    private static final Logger log =
            LoggerFactory.getLogger(DailyBasketballStandingsJob.class);

    private final BasketballSeasonRepository seasonRepo;
    private final BasketballStandingsSyncService standingsSyncService;

    public DailyBasketballStandingsJob(BasketballSeasonRepository seasonRepo,
                                        BasketballStandingsSyncService standingsSyncService) {
        this.seasonRepo = seasonRepo;
        this.standingsSyncService = standingsSyncService;
    }

    /**
     * Her gun 05:30 UTC (TR saat 08:30) — kullanicilar uyaniyor, taze veri
     * hazir olsun. Standings API'si saatlik guncellense de gunluk tek tetik
     * yeterli (lazy sync ucla aktif kullanici tetikler).
     */
    @Scheduled(cron = "0 30 5 * * *", zone = "UTC")
    @SchedulerLock(name = "dailyBasketballStandings", lockAtMostFor = "PT30M")
    public void run() {
        List<BasketballSeason> entries;
        try {
            entries = seasonRepo.findByCoverageStandingsTrue();
        } catch (Exception e) {
            log.warn("Basketbol daily standings: sezon sorgu hata: {}", e.toString());
            return;
        }
        if (entries.isEmpty()) {
            log.debug("Basketbol daily standings: covered sezon yok, atlandi");
            return;
        }

        int synced = 0;
        for (BasketballSeason s : entries) {
            try {
                int n = standingsSyncService.sync(s.getLeague().getId(), s.getSeason());
                if (n > 0) synced++;
            } catch (Exception e) {
                log.warn("Basketbol daily standings hata league={} season={}: {}",
                        s.getLeague().getId(), s.getSeason(), e.toString());
            }
        }
        log.info("Basketbol daily standings sync: {} lig+sezon basariyla tazelendi",
                synced);
    }
}

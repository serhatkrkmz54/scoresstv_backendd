package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Yarınki kapsamlı (covered) liglerin maçları için sakatlık listesini
 * pre-fetch eden günlük iş. Varsayılan 04:30'da çalışır.
 *
 * <p>API güncelleme cadence'ı 4 saat; günlük tek pre-fetch yeterli — kullanıcı
 * detay sayfasını açtığında sakatlık listesi DB'de hazır olur.
 *
 * <p>Quota: ~50-150 yarınki covered maç × 1 çağrı/gün. Trivial.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyInjuriesJob {

    private static final Logger log = LoggerFactory.getLogger(DailyInjuriesJob.class);

    private final FixtureRepository fixtureRepository;
    private final InjuriesSyncService injuriesSyncService;

    public DailyInjuriesJob(FixtureRepository fixtureRepository,
                            InjuriesSyncService injuriesSyncService) {
        this.fixtureRepository = fixtureRepository;
        this.injuriesSyncService = injuriesSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.injuries-cron:0 30 4 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    public void run() {
        Instant start = Instant.now();
        Instant end = start.plus(Duration.ofHours(36));

        List<Fixture> upcoming =
                fixtureRepository.findUpcomingCoveredFixtures(start, end);
        if (upcoming.isEmpty()) {
            return;
        }
        int succeeded = 0;
        int failed = 0;
        for (Fixture f : upcoming) {
            try {
                injuriesSyncService.sync(f.getId());
                succeeded++;
            } catch (ApiException ex) {
                failed++;
                log.warn("Sakatlık sync başarısız (API): fixtureId={} — {}",
                        f.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                failed++;
                log.error("Sakatlık sync beklenmedik hata: fixtureId=" + f.getId(), ex);
            }
        }
        log.info("Sakatlık pre-fetch: {} maç işlendi, {} başarısız.", succeeded, failed);
    }
}

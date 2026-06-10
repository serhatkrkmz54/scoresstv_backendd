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
 * Yarınki kapsamlı (covered) liglerin maçları için tahminleri pre-fetch eden
 * günlük iş. Varsayılan 05:30'da çalışır (sakatlık job'undan sonra).
 *
 * <p>API tahmini saatte bir günceller; tahmin maç öncesi anlamlıdır.
 * Günlük tek pre-fetch yeterlidir — kullanıcı detay sayfasını açtığında
 * tahmin DB'de hazır olur.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyPredictionsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyPredictionsJob.class);

    private final FixtureRepository fixtureRepository;
    private final PredictionsSyncService predictionsSyncService;

    public DailyPredictionsJob(FixtureRepository fixtureRepository,
                               PredictionsSyncService predictionsSyncService) {
        this.fixtureRepository = fixtureRepository;
        this.predictionsSyncService = predictionsSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.predictions-cron:0 30 5 * * *}",
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
                predictionsSyncService.sync(f.getId());
                succeeded++;
            } catch (ApiException ex) {
                failed++;
                log.warn("Tahmin sync başarısız (API): fixtureId={} — {}",
                        f.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                failed++;
                log.error("Tahmin sync beklenmedik hata: fixtureId=" + f.getId(), ex);
            }
        }
        log.info("Tahmin pre-fetch: {} maç işlendi, {} başarısız.", succeeded, failed);
    }
}

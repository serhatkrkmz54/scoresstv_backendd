package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Yarınki (kapsamlı liglerdeki) maçların H2H verisini günlük olarak pre-fetch
 * eden iş. Böylece kullanıcı detay sayfasını açtığında geçmiş karşılaşma
 * listesi DB'de hazır olur — boş "h2h" göstermez.
 *
 * <p>Cron varsayılan {@code 0 0 3 * * *} (her gün 03:00) — window-cron'dan
 * (04:00) önce çalışır ki H2H çekilen maçların temel verileri de gelsin.
 *
 * <p>Quota: ~50-150 yarınki covered maç × 1 çağrı = 50-150/gün. Trivial.
 * Bean yalnız {@code scorestv.football.sync.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyH2hPrefetchJob {

    private static final Logger log = LoggerFactory.getLogger(DailyH2hPrefetchJob.class);

    private final FixtureRepository fixtureRepository;
    private final H2hSyncService h2hSyncService;
    private final FootballProperties properties;

    public DailyH2hPrefetchJob(FixtureRepository fixtureRepository,
                               H2hSyncService h2hSyncService,
                               FootballProperties properties) {
        this.fixtureRepository = fixtureRepository;
        this.h2hSyncService = h2hSyncService;
        this.properties = properties;
        log.info("DailyH2hPrefetchJob aktif: cron='{}' (zone={})",
                "0 0 3 * * *", properties.sync().timezone());
    }

    @Scheduled(
            cron = "${scorestv.football.sync.h2h-prefetch-cron:0 0 3 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "dailyH2hPrefetch", lockAtMostFor = "PT15M")
    public void run() {
        // Yarın için pre-fetch — 0-36 saatlik pencere bugünün gecesi + tüm yarınını kapsar.
        Instant start = Instant.now();
        Instant end = start.plus(Duration.ofHours(36));

        List<Fixture> upcoming =
                fixtureRepository.findUpcomingCoveredFixtures(start, end);
        if (upcoming.isEmpty()) {
            log.info("H2H pre-fetch: yarın için kapsamlı lig maçı yok.");
            return;
        }

        int succeeded = 0;
        int failed = 0;
        for (Fixture f : upcoming) {
            try {
                // Filtresiz (last yok): yeni karşılaşan takımlarda da
                // yakın gelecek + canlı maçlar widget'a düşebilsin.
                h2hSyncService.sync(
                        f.getHomeTeam().getId(), f.getAwayTeam().getId());
                succeeded++;
            } catch (ApiException ex) {
                failed++;
                log.warn("H2H pre-fetch başarısız (API): fixtureId={} — {}",
                        f.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                failed++;
                log.error("H2H pre-fetch beklenmedik hata: fixtureId=" + f.getId(), ex);
            }
        }
        log.info("H2H pre-fetch bitti: {} maç işlendi, {} başarısız.",
                succeeded, failed);
    }
}

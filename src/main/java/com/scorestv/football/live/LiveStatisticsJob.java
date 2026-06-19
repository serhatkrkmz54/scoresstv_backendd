package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixtureStatisticsSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Şu an oynanan maçların istatistiklerini periyodik (varsayılan 60 sn) tazeleyen iş.
 *
 * <p>API-Football'un önerdiği polling: aktif maç için 1 dk. Statlar atomik
 * çağrılarla yenilenir; WebSocket push yapmaz — frontend detay sayfasını her
 * yenilediğinde (HTTP cache 15 sn) güncel statları görür.
 *
 * <p>Bean yalnız {@code scorestv.football.sync.live-enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class LiveStatisticsJob {

    private static final Logger log = LoggerFactory.getLogger(LiveStatisticsJob.class);

    /** "Şu an oynanıyor" sayılan API-Football durum kodları. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    private final FixtureRepository fixtureRepository;
    private final FixtureStatisticsSyncService statsSyncService;
    private final SyncRateLimiter rateLimiter;

    public LiveStatisticsJob(FixtureRepository fixtureRepository,
                             FixtureStatisticsSyncService statsSyncService,
                             SyncRateLimiter rateLimiter) {
        this.fixtureRepository = fixtureRepository;
        this.statsSyncService = statsSyncService;
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.live-statistics-interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "liveStatistics", lockAtMostFor = "PT3M")
    public void run() {
        // JOIN FETCH league: SyncRateLimiter.isCovered() lazy-init hatasını önlemek için.
        List<Fixture> live = fixtureRepository.findByStatusShortInWithLeague(LIVE_STATUSES);
        if (live.isEmpty()) {
            return;
        }
        int total = 0;
        int skipped = 0;
        int written = 0;
        for (Fixture fixture : live) {
            // Tier (covered/non-covered) ve halftime'a göre policy karar verir.
            if (!rateLimiter.shouldSync(SyncRateLimiter.SyncType.STATISTICS, fixture)) {
                skipped++;
                continue;
            }
            try {
                written += statsSyncService.sync(fixture.getId()).statsWritten();
                rateLimiter.markSynced(SyncRateLimiter.SyncType.STATISTICS, fixture.getId());
                total++;
            } catch (ApiException ex) {
                log.warn("İstatistik senkronu başarısız (API): fixtureId={} — {}",
                        fixture.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("İstatistik senkronu beklenmedik hata: fixtureId="
                        + fixture.getId(), ex);
            }
        }
        rateLimiter.evictStale(live.stream()
                .map(Fixture::getId).collect(Collectors.toCollection(HashSet::new)));
        if (written > 0) {
            log.debug("Canlı istatistik tick: {} sync, {} skip, {} satır yazıldı.",
                    total, skipped, written);
        }
    }
}

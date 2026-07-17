package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixturePlayerStatsSyncService;
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
 * Şu an oynanan maçların oyuncu istatistiklerini periyodik tazeleyen iş
 * (varsayılan 120 sn).
 *
 * <p>Player rating'i skor kadar kritik UX değildir; 120 sn varsayılan kota
 * dostudur. WebSocket push yapmaz — frontend detay sayfasını her yenilediğinde
 * (HTTP cache 15 sn) güncel ratingleri görür.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class LivePlayerStatsJob {

    private static final Logger log = LoggerFactory.getLogger(LivePlayerStatsJob.class);

    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    private final FixtureRepository fixtureRepository;
    private final FixturePlayerStatsSyncService syncService;
    private final SyncRateLimiter rateLimiter;
    private final MatchDataReadyBroadcaster readyBroadcaster;

    public LivePlayerStatsJob(FixtureRepository fixtureRepository,
                              FixturePlayerStatsSyncService syncService,
                              SyncRateLimiter rateLimiter,
                              MatchDataReadyBroadcaster readyBroadcaster) {
        this.fixtureRepository = fixtureRepository;
        this.syncService = syncService;
        this.rateLimiter = rateLimiter;
        this.readyBroadcaster = readyBroadcaster;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.live-player-stats-interval-seconds:120}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "livePlayerStats", lockAtMostFor = "PT3M")
    public void run() {
        if (rateLimiter.isLiveBundleEnabled()) {
            return; // batch modu devrede — per-fixture player_stats job devre dışı
        }
        // JOIN FETCH league: SyncRateLimiter.isCovered() lazy-init hatasını önlemek için.
        List<Fixture> live = fixtureRepository.findByStatusShortInWithLeague(LIVE_STATUSES);
        if (live.isEmpty()) {
            return;
        }
        int total = 0;
        int skipped = 0;
        int written = 0;
        for (Fixture fixture : live) {
            if (!rateLimiter.shouldSync(SyncRateLimiter.SyncType.PLAYER_STATS, fixture)) {
                skipped++;
                continue;
            }
            try {
                int w = syncService.sync(fixture.getId()).playersWritten();
                written += w;
                // Yeni oyuncu istatistiği yazıldıysa açık ekranlara ANINDA haber
                // ver: /ready → client silent refetch (cache taze). Oyuncu
                // reytingleri / MOTM / ScoresTV puanı canlı güncellenir.
                if (w > 0) {
                    readyBroadcaster.publish(fixture.getId(), "playerStats");
                }
                rateLimiter.markSynced(SyncRateLimiter.SyncType.PLAYER_STATS, fixture.getId());
                total++;
            } catch (ApiException ex) {
                log.warn("Oyuncu istatistik senkronu başarısız (API): fixtureId={} — {}",
                        fixture.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Oyuncu istatistik senkronu beklenmedik hata: fixtureId="
                        + fixture.getId(), ex);
            }
        }
        rateLimiter.evictStale(live.stream()
                .map(Fixture::getId).collect(Collectors.toCollection(HashSet::new)));
        if (written > 0) {
            log.debug("Canlı oyuncu istatistik tick: {} sync, {} skip, {} satır yazıldı.",
                    total, skipped, written);
        }
    }
}

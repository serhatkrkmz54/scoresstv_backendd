package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
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
 * Şu an oynanan maçların olaylarını periyodik (varsayılan 30 sn) tazeleyen iş.
 *
 * <p>Kart/oyuncu değişikliği/VAR gibi skor-değiştirmeyen olayları yakalar.
 * Goller için ek olarak LiveTickerService skor değişimini saptayıp anında
 * {@link FixtureEventsLiveProcessor#syncAndBroadcast} çağırır — yani goller
 * 30sn beklemez, ticker tick'i içinde (en geç ~15sn) yayınlanır.
 *
 * <p>Diff + WebSocket yayın mantığı {@link FixtureEventsLiveProcessor}'da
 * paylaşılır.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class LiveEventsJob {

    private static final Logger log = LoggerFactory.getLogger(LiveEventsJob.class);

    /** "Şu an oynanıyor" sayılan API-Football durum kodları. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    private final FixtureRepository fixtureRepository;
    private final FixtureEventsLiveProcessor processor;
    private final SyncRateLimiter rateLimiter;

    public LiveEventsJob(FixtureRepository fixtureRepository,
                         FixtureEventsLiveProcessor processor,
                         SyncRateLimiter rateLimiter) {
        this.fixtureRepository = fixtureRepository;
        this.processor = processor;
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.live-events-interval-seconds:30}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "liveEvents", lockAtMostFor = "PT2M")
    public void run() {
        // JOIN FETCH league: SyncRateLimiter.isCovered() lazy-init hatasını önlemek için.
        List<Fixture> live = fixtureRepository.findByStatusShortInWithLeague(LIVE_STATUSES);
        if (live.isEmpty()) {
            return;
        }
        int total = 0;
        int skipped = 0;
        int broadcast = 0;
        for (Fixture fixture : live) {
            if (!rateLimiter.shouldSync(SyncRateLimiter.SyncType.EVENTS, fixture)) {
                skipped++;
                continue;
            }
            try {
                broadcast += processor.syncAndBroadcast(fixture.getId());
                rateLimiter.markSynced(SyncRateLimiter.SyncType.EVENTS, fixture.getId());
                total++;
            } catch (ApiException ex) {
                log.warn("Olay senkronu başarısız (API): fixtureId={} — {}",
                        fixture.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Olay senkronu beklenmedik hata: fixtureId=" + fixture.getId(), ex);
            }
        }
        // Sızıntı önle: artık canlı olmayan maçların kayıtlarını temizle.
        rateLimiter.evictStale(live.stream()
                .map(Fixture::getId).collect(Collectors.toCollection(HashSet::new)));
        if (broadcast > 0) {
            log.info("Canlı olay tick: {} sync, {} skip (tier/HT), {} yeni olay yayıldı.",
                    total, skipped, broadcast);
        }
    }
}

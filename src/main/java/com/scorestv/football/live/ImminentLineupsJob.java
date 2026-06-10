package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixtureLineupsSyncResult;
import com.scorestv.football.sync.FixtureLineupsSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Yaklaşan maçların kadrolarını yoklayan iş.
 *
 * <p>API-Football kadroları kickoff'a <b>20-40 dakika</b> kala yayınlar. Bu
 * job önümüzdeki {@link FootballProperties.Sync#lineupsLookaheadHours} saat
 * içinde başlayacak ve henüz kadrosu olmayan maçlar için
 * {@code /fixtures/lineups} çağırır. Her {@link FootballProperties.Sync#imminentLineupsIntervalMinutes}
 * dakikada bir çalışır.
 *
 * <p>İlk kez kadro yazıldığında {@link LineupBroadcaster} ile
 * {@code lineups.announced} bildirimi gider — frontend detay sayfasını
 * yeniler ve "Kadro açıklandı" göstergesini günceller.
 *
 * <p>Bean yalnız {@code scorestv.football.sync.live-enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.live-enabled", havingValue = "true")
public class ImminentLineupsJob {

    private static final Logger log = LoggerFactory.getLogger(ImminentLineupsJob.class);

    private final FixtureRepository fixtureRepository;
    private final FixtureLineupsSyncService lineupsSyncService;
    private final LineupBroadcaster broadcaster;
    private final FootballProperties properties;

    public ImminentLineupsJob(FixtureRepository fixtureRepository,
                              FixtureLineupsSyncService lineupsSyncService,
                              LineupBroadcaster broadcaster,
                              FootballProperties properties) {
        this.fixtureRepository = fixtureRepository;
        this.lineupsSyncService = lineupsSyncService;
        this.broadcaster = broadcaster;
        this.properties = properties;
        log.info("ImminentLineupsJob aktif: her {} dk'da bir, {} sa pencere yoklanır.",
                properties.sync().imminentLineupsIntervalMinutes(),
                properties.sync().lineupsLookaheadHours());
    }

    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.imminent-lineups-interval-minutes:15}",
            timeUnit = TimeUnit.MINUTES)
    public void run() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofHours(
                properties.sync().lineupsLookaheadHours()));

        List<Fixture> imminent =
                fixtureRepository.findImminentWithoutLineups(now, until);
        if (imminent.isEmpty()) {
            return;
        }

        int announced = 0;
        for (Fixture fixture : imminent) {
            try {
                FixtureLineupsSyncResult result = lineupsSyncService.sync(fixture.getId());
                // Sorgu zaten kadrosuz maçları seçti — yazılan > 0 ise ilk
                // açıklamadır → bildirim.
                if (result.lineupsWritten() > 0) {
                    broadcaster.broadcastAnnounced(fixture.getId());
                    announced++;
                }
            } catch (ApiException ex) {
                log.warn("Yaklaşan kadro sync başarısız: fixtureId={} — {}",
                        fixture.getId(), ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("Yaklaşan kadro sync beklenmedik hata: fixtureId="
                        + fixture.getId(), ex);
            }
        }
        if (announced > 0) {
            log.info("Yaklaşan kadrolar: {} maç tarandı, {} maçın kadrosu yeni açıklandı.",
                    imminent.size(), announced);
        }
    }
}

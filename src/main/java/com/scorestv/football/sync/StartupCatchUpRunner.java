package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

/**
 * Uygulama acilirken downtime sirasinda kacirilan senkronlari yakalayan iş.
 *
 * <p><b>Sebep:</b> Sistem 30dk-birkac saat kapali kaldiginda:
 * <ul>
 *   <li>{@link com.scorestv.football.live.LiveTickerJob} (15sn) — restart
 *       sonrasi ilk tick'te canli maclar yakalar (stuck-LIVE detection dahil).</li>
 *   <li>{@link TodayRefreshJob} (15dk) — bugunun maclarini yakalar ama
 *       <i>15 dk bekler</i>.</li>
 *   <li>{@link FinishedMatchFinalSyncJob} (15dk, 30dk-24sa pencere) —
 *       biten covered maclar icin stats/lineups/events/playerStats yakalar
 *       ama <i>15 dk bekler</i>.</li>
 * </ul>
 *
 * <p>Bu runner uygulama hazir olur olmaz <i>hemen</i> bir kez calisir:
 * <ol>
 *   <li>Bugun ve dunun basic fixtures'ini refresh eder (skor/status)</li>
 *   <li>Su an LIVE olan covered maclar icin events/stats/playerStats sync</li>
 *   <li>Son 24 saatte biten covered maclar icin stats/lineups/events/
 *       playerStats sync (FinishedMatchFinalSyncJob ayni isi yapacak ama
 *       biz dakikalar yerine saniyeler icinde yetisiriz)</li>
 * </ol>
 *
 * <p>{@code @Async} ile background thread'de calisir, startup'i blokmaz.
 * {@code @ConditionalOnProperty} ile sync devre disiyken bean olusturulmaz.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class StartupCatchUpRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCatchUpRunner.class);

    /** Hangi maclar "currently LIVE" sayilir? LiveTickerService ile ayni set. */
    private static final Set<String> LIVE_STATUSES = Set.of(
            "1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE");

    /** Son N saatte biten maclar finalize edilir (FT/AET/PEN). */
    private static final int FINISHED_LOOKBACK_HOURS = 24;

    private final FootballProperties properties;
    private final FixtureSyncService fixtureSyncService;
    private final FixtureRepository fixtureRepository;
    private final FixtureStatisticsSyncService statsSyncService;
    private final FixturePlayerStatsSyncService playerStatsSyncService;
    private final FixtureLineupsSyncService lineupsSyncService;
    private final FixtureEventsSyncService eventsSyncService;

    public StartupCatchUpRunner(FootballProperties properties,
                                FixtureSyncService fixtureSyncService,
                                FixtureRepository fixtureRepository,
                                FixtureStatisticsSyncService statsSyncService,
                                FixturePlayerStatsSyncService playerStatsSyncService,
                                FixtureLineupsSyncService lineupsSyncService,
                                FixtureEventsSyncService eventsSyncService) {
        this.properties = properties;
        this.fixtureSyncService = fixtureSyncService;
        this.fixtureRepository = fixtureRepository;
        this.statsSyncService = statsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.lineupsSyncService = lineupsSyncService;
        this.eventsSyncService = eventsSyncService;
    }

    /**
     * Uygulama hazir olunca tetiklenir. Async — startup beklemez.
     * Sira ile: basic refresh → live recovery → finished recovery.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onReady() {
        log.info("StartupCatchUp basliyor — downtime recovery devrede.");
        long started = System.currentTimeMillis();
        try {
            int basic = refreshTodayAndYesterday();
            int liveCount = recoverLiveFixtures();
            int finishedCount = recoverRecentlyFinished();
            long elapsed = System.currentTimeMillis() - started;
            log.info("StartupCatchUp bitti ({} ms): basic={}, live recover={}, finished recover={}",
                    elapsed, basic, liveCount, finishedCount);
        } catch (RuntimeException ex) {
            log.error("StartupCatchUp beklenmedik hata", ex);
        }
    }

    /** Bugun ve dunun fixtures'ini iki ayri /fixtures?date= cagrisiyla tazeler. */
    private int refreshTodayAndYesterday() {
        ZoneId zone = ZoneId.of(properties.sync().timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate yesterday = today.minusDays(1);
        int total = 0;
        total += syncDateQuietly(today);
        total += syncDateQuietly(yesterday);
        return total;
    }

    private int syncDateQuietly(LocalDate date) {
        try {
            int n = fixtureSyncService.syncDate(date);
            log.info("StartupCatchUp basic refresh: {} — {} mac", date, n);
            return n;
        } catch (ApiException ex) {
            log.warn("StartupCatchUp basic refresh API hatasi: {} — {}", date, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("StartupCatchUp basic refresh beklenmedik hata: {} — {}",
                    date, ex.getMessage());
        }
        return 0;
    }

    /**
     * Su an LIVE statulu maclar icin per-mac events/stats/playerStats sync.
     * Lineups zaten kickoff'tan once aciklanmis durumda — recovery'e gerek
     * yok (LiveTickerService stuck detection halletecek).
     */
    private int recoverLiveFixtures() {
        List<Fixture> live = fixtureRepository.findLiveWithDetails(LIVE_STATUSES);
        if (live.isEmpty()) return 0;
        int processed = 0;
        for (Fixture f : live) {
            Long id = f.getId();
            runQuietly("live-events", id, () -> eventsSyncService.sync(id));
            runQuietly("live-stats", id, () -> statsSyncService.sync(id));
            runQuietly("live-playerStats", id, () -> playerStatsSyncService.sync(id));
            processed++;
        }
        return processed;
    }

    /**
     * Son 24 saatte biten covered maclar icin stats/lineups/events/playerStats
     * sync. FinishedMatchFinalSyncJob ayni isi yapacak ama bu yontemle
     * restart'tan saniyeler sonra hemen hazir oluruz.
     */
    private int recoverRecentlyFinished() {
        Instant now = Instant.now();
        Instant start = now.minus(FINISHED_LOOKBACK_HOURS, ChronoUnit.HOURS);
        // FinishedMatchFinalSyncJob ile ayni query — "MIN_AGE 0" gibi davranir
        // (su anki ana kadar tum biten covered maclar).
        List<Fixture> finished =
                fixtureRepository.findRecentlyFinishedCoveredFixtures(start, now);
        if (finished.isEmpty()) return 0;
        int processed = 0;
        for (Fixture f : finished) {
            Long id = f.getId();
            runQuietly("finish-stats", id, () -> statsSyncService.sync(id));
            runQuietly("finish-playerStats", id, () -> playerStatsSyncService.sync(id));
            runQuietly("finish-lineups", id, () -> lineupsSyncService.sync(id));
            runQuietly("finish-events", id, () -> eventsSyncService.sync(id));
            processed++;
        }
        return processed;
    }

    private void runQuietly(String module, Long fixtureId, Runnable r) {
        try {
            r.run();
        } catch (ApiException ex) {
            log.warn("StartupCatchUp {} sync basarisiz (API): fixtureId={} — {}",
                    module, fixtureId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("StartupCatchUp {} sync beklenmedik hata: fixtureId={} — {}",
                    module, fixtureId, ex.getMessage());
        }
    }
}

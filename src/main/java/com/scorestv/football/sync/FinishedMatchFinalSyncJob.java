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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Yeni biten covered maçlar için bir kez "son tazeleme" yapan iş.
 *
 * <p><b>Sebep:</b> API-Football maç bittikten sonra istatistikler / oyuncu
 * rating'leri / kadrolarda ~30-90 dakika içinde küçük düzeltmeler yayınlar.
 * Lazy sync sadece kullanıcı detayı açtığında devreye girer; popüler olmayan
 * bir maçı kimse açmazsa son düzeltmeler kaçar. Bu job kullanıcıdan bağımsız
 * olarak biten covered maçları arar ve <b>tek bir kez</b> son tazeleme çağrısı
 * gönderir.
 *
 * <p><b>Quota:</b> Yalnız covered ligler (varsayılan ~30-50 lig). Günde
 * ~50-150 covered FT maç × 4 endpoint (stats, playerStats, lineups, events)
 * = 200-600 API çağrısı. Trivial.
 *
 * <p><b>Idempotency:</b> Bellekte {@code finalizedIds} setiyle aynı maç için
 * tekrar çağrı yapılmaz. Bean yeniden başlatıldığında set sıfırlanır — biten
 * maç periyodik query penceresinden çıktığında zaten tekrar seçilmez.
 *
 * <p>Bean yalnız {@code scorestv.football.sync.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class FinishedMatchFinalSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FinishedMatchFinalSyncJob.class);

    /**
     * Maçtan ne kadar süre geçmiş olmalı: FT'den HEMEN sonra değil, API'nin
     * düzeltmeleri yayınlama süresini bekle. 30 dk minimum, 24 saat maksimum.
     *
     * <p><b>Neden 24sa?</b> Downtime recovery: uygulama uzun sure (4-6sa)
     * kapali kalirsa, pencere dar olunca biten maclarin stats/playerStats/
     * lineups/events guncellemesi kacirilir. 24sa pencere, bir sonraki
     * tick'te tum bunlari yakalar. Kotaya etkisi: ~50-100 ekstra covered FT
     * mac/gun × 4 endpoint = 200-400 ekstra cagri (idempotent, ayni mac iki
     * kere finalize edilmez).
     */
    private static final Duration MIN_AGE = Duration.ofMinutes(30);
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final FixtureRepository fixtureRepository;
    private final FixtureStatisticsSyncService statsSyncService;
    private final FixturePlayerStatsSyncService playerStatsSyncService;
    private final FixtureLineupsSyncService lineupsSyncService;
    private final FixtureEventsSyncService eventsSyncService;

    /** Bu instance ömründe son tazelemesi yapılmış maç id'leri. */
    private final Set<Long> finalizedIds = ConcurrentHashMap.newKeySet();

    public FinishedMatchFinalSyncJob(FixtureRepository fixtureRepository,
                                     FixtureStatisticsSyncService statsSyncService,
                                     FixturePlayerStatsSyncService playerStatsSyncService,
                                     FixtureLineupsSyncService lineupsSyncService,
                                     FixtureEventsSyncService eventsSyncService) {
        this.fixtureRepository = fixtureRepository;
        this.statsSyncService = statsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.lineupsSyncService = lineupsSyncService;
        this.eventsSyncService = eventsSyncService;
    }

    /**
     * Her {@code scorestv.football.sync.final-finished-interval-minutes}
     * (varsayılan 15) dakikada bir, kickoff'undan 30dk-6sa önce biten covered
     * maçları tarar; daha önce finalize edilmemişleri tek seferlik sync eder.
     */
    @Scheduled(
            fixedDelayString = "${scorestv.football.sync.final-finished-interval-minutes:15}",
            timeUnit = TimeUnit.MINUTES)
    public void run() {
        Instant now = Instant.now();
        Instant start = now.minus(MAX_AGE);
        Instant end = now.minus(MIN_AGE);

        List<Fixture> finished =
                fixtureRepository.findRecentlyFinishedCoveredFixtures(start, end);
        if (finished.isEmpty()) {
            return;
        }
        int processed = 0;
        int skipped = 0;
        for (Fixture f : finished) {
            if (!finalizedIds.add(f.getId())) {
                skipped++;
                continue;  // Daha önce işlendi.
            }
            finalize(f);
            processed++;
        }
        // Eski id'leri set'ten çıkar (pencere dışına çıkmış olanlar): bellek
        // büyümesin. Sadece pencere içindeki id'leri tut.
        Set<Long> currentWindow = new HashSet<>(finished.size());
        for (Fixture f : finished) currentWindow.add(f.getId());
        finalizedIds.retainAll(currentWindow);

        if (processed > 0) {
            log.info("FT-final sync: {} maç finalize edildi, {} zaten yapılmıştı.",
                    processed, skipped);
        }
    }

    /** Bir maç için stats / playerStats / lineups / events tek seferlik sync. */
    private void finalize(Fixture f) {
        Long id = f.getId();
        runQuietly("stats", id, () -> statsSyncService.sync(id));
        runQuietly("playerStats", id, () -> playerStatsSyncService.sync(id));
        runQuietly("lineups", id, () -> lineupsSyncService.sync(id));
        runQuietly("events", id, () -> eventsSyncService.sync(id));
    }

    private void runQuietly(String module, Long fixtureId, Runnable r) {
        try {
            r.run();
        } catch (ApiException ex) {
            log.warn("FT-final {} sync başarısız (API): fixtureId={} — {}",
                    module, fixtureId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("FT-final {} sync beklenmedik hata: fixtureId={} — {}",
                    module, fixtureId, ex.getMessage());
        }
    }
}

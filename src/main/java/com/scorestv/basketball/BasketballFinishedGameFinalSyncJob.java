package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yeni biten basketbol maclari icin bir kez "son tazeleme" yapan is.
 *
 * <p><b>Sebep:</b> API-Basketball mac bittikten sonra istatistikler ve oyuncu
 * stats'larinda ~30-90 dakika icinde kucuk duzeltmeler yayinlar. LazySync
 * sadece kullanici detayi acinca devreye girer; populer olmayan macin son
 * duzeltmeleri kacirilir. Bu job kullanicidan bagimsiz olarak biten maclari
 * tarar ve <b>tek seferlik</b> team + player stats sync gonderir.
 *
 * <p><b>Quota:</b> Gunde ~30-80 biten basketbol mac × 2 endpoint (team stats,
 * player stats) = 60-160 API cagrisi. Trivial — kotaya etkisi minimum.
 *
 * <p><b>Idempotency:</b> Bellekte {@code finalizedIds} setiyle ayni mac iki
 * kere finalize edilmez. Bean restart edilirse set sifirlanir — eski maclar
 * pencere disina cikar ve tekrar secilmez.
 *
 * <p>Futbol {@code FinishedMatchFinalSyncJob}'in basketbol esi.
 * Bean yalniz {@code scorestv.basketball.enabled=true} ile aktif.
 */
@Component
@ConditionalOnProperty(name = "scorestv.basketball.enabled", havingValue = "true")
public class BasketballFinishedGameFinalSyncJob {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballFinishedGameFinalSyncJob.class);

    /** Mactan minimum gecmis sure — API'nin duzeltmelerini yayinlamasini bekle. */
    private static final Duration MIN_AGE = Duration.ofMinutes(30);

    /** Maks pencere — downtime recovery: 6sa downtime sonrasi tum biten maclari yakala. */
    private static final Duration MAX_AGE = Duration.ofHours(24);

    /** Cron: 15 dakikada bir kontrol — biten maclar birikmesin. */
    private static final long FIXED_DELAY_MS = 15 * 60_000L;

    private final BasketballGameRepository gameRepo;
    private final BasketballGameStatsSyncService statsSyncService;

    /** Bu instance omru icinde son tazelemesi yapilmis mac id'leri. */
    private final Set<Long> finalizedIds = ConcurrentHashMap.newKeySet();

    public BasketballFinishedGameFinalSyncJob(BasketballGameRepository gameRepo,
                                                BasketballGameStatsSyncService statsSyncService) {
        this.gameRepo = gameRepo;
        this.statsSyncService = statsSyncService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS, initialDelay = 60_000L)
    @SchedulerLock(name = "basketballFinishedGameFinalSync", lockAtMostFor = "PT30M")
    public void run() {
        Instant now = Instant.now();
        Instant from = now.minus(MAX_AGE);
        Instant until = now.minus(MIN_AGE);

        List<BasketballGame> games;
        try {
            games = gameRepo.findRecentlyFinished(from, until);
        } catch (Exception e) {
            log.warn("Basketbol finalize: biten mac sorgusu hata: {}", e.toString());
            return;
        }

        int processed = 0;
        for (BasketballGame g : games) {
            if (!finalizedIds.add(g.getId())) continue;   // idempotent
            try {
                statsSyncService.syncBoth(g.getId());
                processed++;
            } catch (Exception e) {
                log.warn("Basketbol finalize hata id={}: {}", g.getId(), e.toString());
                // Hata olursa setten cikar ki bir sonraki tick tekrar denesin.
                finalizedIds.remove(g.getId());
            }
        }
        if (processed > 0) {
            log.info("Basketbol finalize sync: {} mac finalize edildi", processed);
        }
    }
}

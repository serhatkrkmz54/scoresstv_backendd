package com.scorestv.football;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API-Football kota durumunu merkezi olarak takip eder.
 *
 * <p>{@link ApiFootballClient} her yanitta {@link #updateFromHeaders} cagirir;
 * boylelikle tum sync job'lar, lazy sync ve worker SAYISAL kalan kota'ya gore
 * karar verebilir (sadece HTTP throttle'a guvenmek yerine).
 *
 * <p>API yanit header'lari:
 * <ul>
 *   <li>{@code x-ratelimit-requests-limit} — gunluk limit (orn. 75000)</li>
 *   <li>{@code x-ratelimit-requests-remaining} — gunluk kalan</li>
 *   <li>{@code X-RateLimit-Limit} — dakikalik limit (orn. 500)</li>
 *   <li>{@code X-RateLimit-Remaining} — dakikalik kalan</li>
 * </ul>
 *
 * <p>Header gelene kadar bilinmez (server start sonrasi ilk istegi bekler).
 * O ana kadar {@code dailyRemaining = -1} doner; caller "bilinmiyor → ihtiyatli
 * davran" diye yorumlamali.
 */
@Component
public class ApiQuotaTracker {

    private static final Logger log = LoggerFactory.getLogger(ApiQuotaTracker.class);

    /** -1 = henuz API yaniti gelmemis (bilinmiyor). */
    private final AtomicInteger dailyLimit = new AtomicInteger(-1);
    private final AtomicInteger dailyRemaining = new AtomicInteger(-1);
    private final AtomicInteger minuteLimit = new AtomicInteger(-1);
    private final AtomicInteger minuteRemaining = new AtomicInteger(-1);
    private final AtomicReference<Instant> lastUpdatedAt = new AtomicReference<>();
    private final AtomicLong totalRequestsSinceStart = new AtomicLong(0);
    /**
     * 429 (rate limit) sonrasi GLOBAL cooldown — <b>Redis'te paylasilir</b> ki
     * bir node 429 yiyip cooldown'a girince TUM node'lar ayni cooldown'a uysun.
     * Multi-instance'ta "bir node ban yer, otekiler hammer'lamaya devam eder"
     * sorununu onler. Deger = bitis epoch ms; TTL = kalan cooldown (otomatik
     * temizlenir). {@link ApiFootballClient} bu sure boyunca tum cagrilari reddeder.
     */
    private static final String COOLDOWN_KEY = "scorestv:apifootball:cooldownUntil";

    private final StringRedisTemplate redis;

    /**
     * GLOBAL (hesap-geneli) hız yöneticisi — TÜM sporlar ORTAK.
     *
     * <p>API-Football limiti ANAHTAR bazlıdır ve tek anahtar futbol + basketbol +
     * voleybol için birlikte kullanılır (1200 istek/dk tavan). Futbolun kendi
     * saniyelik kovası vardı ama basketbol/voleybol'ün hiç yoktu; üç spor
     * birlikte 1200/dk'yı aşıp <i>"exceeded the limit of requests per minute"</i>
     * hatası alınıyordu. Bu token-bucket, üç sporun BİRLİKTE saniyede en fazla
     * {@code globalRatePerSec} istek yapmasını sağlar (varsayılan 18 → ~1080/dk,
     * 1200 tavanının güvenli altında). Her API isteğinden ÖNCE
     * {@link #acquireGlobalSlot()} çağrılır. Tek instance → in-process yeterli
     * (Redis'e gerek yok); fair semaphore → LIVE istekler aç kalmaz (FIFO).
     */
    private final Semaphore globalBucket;
    private final int globalCapacity;
    private final ScheduledExecutorService globalRefiller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "api-global-rate-refill");
                t.setDaemon(true);
                return t;
            });

    public ApiQuotaTracker(
            StringRedisTemplate redis,
            @Value("${scorestv.api-football.global-rate-per-second:18}") int globalRatePerSec) {
        this.redis = redis;
        this.globalCapacity = Math.max(1, globalRatePerSec);
        this.globalBucket = new Semaphore(globalCapacity, true); // fair → FIFO
        // Düz refill: her 1000/N ms'de 1 token bırak (kapasiteye kadar). Saniye
        // başı tek seferde N token yerine dağıtık → saniye-burst de yumuşar.
        final long intervalMs = Math.max(1L, 1000L / globalCapacity);
        globalRefiller.scheduleAtFixedRate(() -> {
            if (globalBucket.availablePermits() < globalCapacity) {
                globalBucket.release(1);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("API GLOBAL rate limiter hazir: {} istek/sn (~{}/dk) — "
                + "futbol+basketbol+voleybol ORTAK anahtar limiti (1200/dk) altinda.",
                globalCapacity, globalCapacity * 60);
    }

    /**
     * Hesap-geneli global hız slotu al (TÜM sporlar). Slot yoksa bir sonraki
     * token bırakılana kadar BLOKLAR (~≤1 sn). Her API isteğinden ÖNCE çağrılır;
     * futbol/basketbol/voleybol istemcilerinin hepsi bu tek bucket'ı paylaşır.
     */
    public void acquireGlobalSlot() {
        try {
            globalBucket.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdownGlobalRefiller() {
        globalRefiller.shutdownNow();
    }

    /**
     * Yanit header'larindan kotayi gunceller. Beklenmedik degerler (null, parse
     * hatasi) sessizce yutulur.
     */
    public void updateFromHeaders(HttpHeaders headers) {
        if (headers == null) return;
        updateInt(headers, "x-ratelimit-requests-limit", dailyLimit);
        updateInt(headers, "x-ratelimit-requests-remaining", dailyRemaining);
        updateInt(headers, "X-RateLimit-Limit", minuteLimit);
        updateInt(headers, "X-RateLimit-Remaining", minuteRemaining);
        lastUpdatedAt.set(Instant.now());
        totalRequestsSinceStart.incrementAndGet();
    }

    private static void updateInt(HttpHeaders h, String key, AtomicInteger target) {
        String v = h.getFirst(key);
        if (v == null || v.isBlank()) return;
        try {
            target.set(Integer.parseInt(v.trim()));
        } catch (NumberFormatException ex) {
            // ignore
        }
    }

    /** Bilinen gunluk kalan kota. -1 = henuz bilinmiyor. */
    public int getDailyRemaining() {
        return dailyRemaining.get();
    }

    public int getDailyLimit() {
        return dailyLimit.get();
    }

    public int getMinuteRemaining() {
        return minuteRemaining.get();
    }

    public int getMinuteLimit() {
        return minuteLimit.get();
    }

    /**
     * Gunluk kalan kotanin yuzdesi (0-100). -1 = bilinmiyor.
     */
    public int getDailyRemainingPercent() {
        int limit = dailyLimit.get();
        int remaining = dailyRemaining.get();
        if (limit <= 0 || remaining < 0) return -1;
        return (int) ((long) remaining * 100 / limit);
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt.get();
    }

    public long getTotalRequestsSinceStart() {
        return totalRequestsSinceStart.get();
    }

    /**
     * 429 sonrasi global cooldown baslatir. Mevcut cooldown daha uzun ise
     * kisaltilmaz (max korunur). null/sifir/negatif sure yok sayilir.
     */
    public void startCooldown(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            long candidate = now + duration.toMillis();
            // Mevcut cooldown daha uzun ise kisaltma (max korunur).
            String existing = redis.opsForValue().get(COOLDOWN_KEY);
            long until = candidate;
            if (existing != null) {
                try {
                    until = Math.max(Long.parseLong(existing), candidate);
                } catch (NumberFormatException ignore) {
                    // bozuk deger — candidate kullan
                }
            }
            long ttlMs = Math.max(1L, until - now);
            redis.opsForValue().set(COOLDOWN_KEY, Long.toString(until),
                    Duration.ofMillis(ttlMs));
            log.warn("API-Football cooldown baslatildi: ~{} sn (429 rate limit)",
                    duration.toSeconds());
        } catch (Exception e) {
            log.warn("Cooldown Redis'e yazilamadi: {}", e.toString());
        }
    }

    /** Cooldown bitene kadar kalan ms; aktif degilse 0. Redis paylasimli. */
    public long cooldownRemainingMillis() {
        try {
            String v = redis.opsForValue().get(COOLDOWN_KEY);
            if (v == null) return 0L;
            return Math.max(0L, Long.parseLong(v) - System.currentTimeMillis());
        } catch (Exception e) {
            // Redis erisilemezse cooldown'i uygulamayi engelleme (fail-open).
            return 0L;
        }
    }

    /** Su an 429 cooldown'i aktif mi? */
    public boolean isInCooldown() {
        return cooldownRemainingMillis() > 0;
    }

    /**
     * Quota state ozeti — JSON'a serialize edilebilir (admin endpoint).
     */
    public QuotaSnapshot snapshot() {
        return new QuotaSnapshot(
                dailyLimit.get(),
                dailyRemaining.get(),
                getDailyRemainingPercent(),
                minuteLimit.get(),
                minuteRemaining.get(),
                lastUpdatedAt.get(),
                totalRequestsSinceStart.get());
    }

    public record QuotaSnapshot(
            int dailyLimit,
            int dailyRemaining,
            int dailyRemainingPercent,
            int minuteLimit,
            int minuteRemaining,
            Instant lastUpdatedAt,
            long totalRequestsSinceStart
    ) {}
}

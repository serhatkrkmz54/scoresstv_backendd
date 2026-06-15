package com.scorestv.football;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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
     * 429 (rate limit) sonrasi global cooldown'in bitis ani (epoch ms).
     * 0 = cooldown yok. {@link ApiFootballClient} bu sure boyunca tum cagrilari
     * reddeder; boylece firewall blok riski olan "429 sonrasi hammer'lama"
     * onlenir (bkz. api-football ratelimit dokumantasyonu).
     */
    private final AtomicLong cooldownUntilEpochMs = new AtomicLong(0);

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
        long until = System.currentTimeMillis() + duration.toMillis();
        cooldownUntilEpochMs.accumulateAndGet(until, Math::max);
        log.warn("API-Football cooldown baslatildi: ~{} sn (429 rate limit)",
                duration.toSeconds());
    }

    /** Cooldown bitene kadar kalan ms; aktif degilse 0. */
    public long cooldownRemainingMillis() {
        return Math.max(0L, cooldownUntilEpochMs.get() - System.currentTimeMillis());
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

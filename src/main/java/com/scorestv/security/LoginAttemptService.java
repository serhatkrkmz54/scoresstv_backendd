package com.scorestv.security;

import com.scorestv.settings.SettingsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Hatali giris denemelerini Redis'te sayar; esik asilinca hesabi gecici kilitler.
 * Esik (deneme sayisi) ve kilit suresi SettingsService uzerinden ADMIN tarafindan
 * degistirilebilir.
 *
 * Redis anahtarlari:
 *   login:fail:{email}  -> sayac, TTL kadar pencerede tutulur
 *   login:lock:{email}  -> varsa hesap kilitli, TTL = kilit suresi
 */
@Service
public class LoginAttemptService {

    private static final String FAIL_KEY = "login:fail:";
    private static final String LOCK_KEY = "login:lock:";

    private final StringRedisTemplate redis;
    private final SettingsService settingsService;

    public LoginAttemptService(StringRedisTemplate redis, SettingsService settingsService) {
        this.redis = redis;
        this.settingsService = settingsService;
    }

    /** Hesap kilitliyse kalan sure (saniye), degilse 0. */
    public long lockSecondsRemaining(String email) {
        Long ttl = redis.getExpire(LOCK_KEY + email, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    /** Basarisiz deneme kaydeder; esige ulasilinca hesabi kilitler. */
    public void recordFailure(String email) {
        int maxAttempts = settingsService.getMaxFailedAttempts();
        Duration lockDuration = Duration.ofMinutes(settingsService.getLockoutMinutes());

        String failKey = FAIL_KEY + email;
        Long count = redis.opsForValue().increment(failKey);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            // Ilk hatadan itibaren sayac penceresi baslar.
            redis.expire(failKey, lockDuration);
        }
        if (count >= maxAttempts) {
            redis.opsForValue().set(LOCK_KEY + email, "1", lockDuration);
            redis.delete(failKey);
        }
    }

    /** Basarili giris: sayac ve kilit temizlenir. */
    public void recordSuccess(String email) {
        redis.delete(FAIL_KEY + email);
        redis.delete(LOCK_KEY + email);
    }
}

package com.scorestv.settings;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uygulama ayarlarini okur/gunceller. Sik okunan ayarlar Redis'te cache'lenir;
 * guncelleme yapildiginda cache temizlenir.
 */
@Service
public class SettingsService {

    /** Redis cache adi. */
    public static final String CACHE = "settings";

    public static final String MAX_FAILED_ATTEMPTS = "auth.max-failed-attempts";
    public static final String LOCKOUT_MINUTES = "auth.lockout-minutes";

    private static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    private static final int DEFAULT_LOCKOUT_MINUTES = 15;

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    /** Tum ayarlar - admin goruntuleme icin. */
    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        Map<String, String> map = new LinkedHashMap<>();
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    @Cacheable(value = CACHE, key = "'max-failed-attempts'")
    public int getMaxFailedAttempts() {
        return intValue(MAX_FAILED_ATTEMPTS, DEFAULT_MAX_FAILED_ATTEMPTS);
    }

    @Cacheable(value = CACHE, key = "'lockout-minutes'")
    public int getLockoutMinutes() {
        return intValue(LOCKOUT_MINUTES, DEFAULT_LOCKOUT_MINUTES);
    }

    /** Brute-force ayarlarini gunceller; ardindan cache temizlenir. */
    @CacheEvict(value = CACHE, allEntries = true)
    @Transactional
    public void updateLoginSecurity(int maxFailedAttempts, int lockoutMinutes) {
        save(MAX_FAILED_ATTEMPTS, String.valueOf(maxFailedAttempts));
        save(LOCKOUT_MINUTES, String.valueOf(lockoutMinutes));
    }

    private void save(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(AppSetting::new);
        setting.setKey(key);
        setting.setValue(value);
        repository.save(setting);
    }

    private int intValue(String key, int defaultValue) {
        return repository.findById(key)
                .map(AppSetting::getValue)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}

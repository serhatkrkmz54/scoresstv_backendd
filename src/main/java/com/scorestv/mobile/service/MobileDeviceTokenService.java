package com.scorestv.mobile.service;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.web.dto.DeviceTokenResponse;
import com.scorestv.mobile.web.dto.RegisterDeviceTokenRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * FCM token kayit/guncelleme servisi (anonim cihaz tabanli).
 *
 * <p>Her POST cagrisi: token yoksa INSERT, varsa locale/appVersion/lastSeenAt
 * UPDATE. Cihaz uninstall sonrasi reinstall yeni token alir; eski token
 * sonunda gecersizdir (FCM unregistered hatasi ile dispatcher temizler).
 */
@Service
public class MobileDeviceTokenService {

    private static final Logger log =
            LoggerFactory.getLogger(MobileDeviceTokenService.class);

    private final MobileDeviceTokenRepository repository;

    public MobileDeviceTokenService(MobileDeviceTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DeviceTokenResponse registerOrUpdate(RegisterDeviceTokenRequest req) {
        String token = req.fcmToken().trim();
        String platform = req.platform().toLowerCase(Locale.ROOT);
        String locale = (req.locale() == null || req.locale().isBlank())
                ? "tr" : req.locale().toLowerCase(Locale.ROOT);

        MobileDeviceToken existing = repository.findByFcmToken(token).orElse(null);
        if (existing != null) {
            existing.setPlatform(platform);
            existing.setAppVersion(req.appVersion());
            existing.setLocale(locale);
            existing.setLastSeenAt(Instant.now());
            repository.save(existing);
            log.debug("Device token guncellendi: id={} platform={}", existing.getId(), platform);
            return new DeviceTokenResponse(existing.getId(), false);
        }

        MobileDeviceToken fresh = new MobileDeviceToken();
        fresh.setFcmToken(token);
        fresh.setPlatform(platform);
        fresh.setAppVersion(req.appVersion());
        fresh.setLocale(locale);
        fresh.setLastSeenAt(Instant.now());
        MobileDeviceToken saved = repository.save(fresh);
        log.info("Yeni device token kaydedildi: id={} platform={} version={}",
                saved.getId(), platform, req.appVersion());
        return new DeviceTokenResponse(saved.getId(), true);
    }

    /**
     * FCM dispatcher tarafindan cagrilir — token gecersiz oldugunda
     * (UNREGISTERED hatasi) cihaz kaydini ve prefs'lerini siler (cascade).
     */
    @Transactional
    public void invalidateToken(String fcmToken) {
        repository.deleteByFcmToken(fcmToken);
        log.info("Gecersiz FCM token silindi: {}", fcmToken);
    }
}

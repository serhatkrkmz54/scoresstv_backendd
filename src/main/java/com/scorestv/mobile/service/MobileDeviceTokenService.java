package com.scorestv.mobile.service;

import com.scorestv.mobile.domain.MobileDeviceToken;
import com.scorestv.mobile.domain.MobileDeviceTokenRepository;
import com.scorestv.mobile.web.dto.DeviceTokenResponse;
import com.scorestv.mobile.web.dto.RegisterDeviceTokenRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
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
    /** Self-proxy — @Transactional metoda proxy uzerinden gidip dup-key
     *  yarisinda YENI transaction'da UPDATE olarak yeniden deneyebilmek icin. */
    private final MobileDeviceTokenService self;

    public MobileDeviceTokenService(MobileDeviceTokenRepository repository,
                                    @Lazy MobileDeviceTokenService self) {
        this.repository = repository;
        this.self = self;
    }

    public DeviceTokenResponse registerOrUpdate(RegisterDeviceTokenRequest req) {
        return registerOrUpdate(req, null);
    }

    /**
     * Orkestrator (transaction DISI): ayni fcm_token'i iki istek AYNI ANDA
     * register ederse ilk INSERT kazanir, ikincisi unique (fcm_token) ihlali
     * alir. Burada onu yakalayip UPDATE yoluna cevirerek dup-key/500 hatasini
     * onleriz. Ilk deneme rollback oldugundan yeniden deneme YENI bir
     * transaction'da (self-proxy) calisir; token artik var → UPDATE yolu.
     */
    public DeviceTokenResponse registerOrUpdate(RegisterDeviceTokenRequest req,
                                                Long appUserId) {
        try {
            return self.doRegisterOrUpdate(req, appUserId);
        } catch (DataIntegrityViolationException race) {
            log.debug("Device token yaris (dup fcm_token) — UPDATE olarak yeniden deniyorum");
            return self.doRegisterOrUpdate(req, appUserId);
        }
    }

    /**
     * @param appUserId giris yapmis kullanicinin id'si (yoksa null — anonim).
     *                  Cihazi kullaniciya baglar; kullaniciya ozel push (oyun
     *                  sonucu) icin. Her register'da guncellenir (aktif
     *                  kullaniciyi yansitir; logout sonrasi anonim register
     *                  null'a ceker).
     */
    @Transactional
    public DeviceTokenResponse doRegisterOrUpdate(RegisterDeviceTokenRequest req,
                                                  Long appUserId) {
        String token = req.fcmToken().trim();
        String platform = req.platform().toLowerCase(Locale.ROOT);
        String locale = (req.locale() == null || req.locale().isBlank())
                ? "tr" : req.locale().toLowerCase(Locale.ROOT);

        String countryCode = normalizeCountry(req.countryCode());

        MobileDeviceToken existing = repository.findByFcmToken(token).orElse(null);
        if (existing != null) {
            existing.setPlatform(platform);
            existing.setAppVersion(req.appVersion());
            existing.setLocale(locale);
            // Eski app surumu country gondermezse mevcut degeri koru.
            if (countryCode != null) {
                existing.setCountryCode(countryCode);
            }
            // Eski app surumu notifyNews gondermezse mevcut degeri koru
            // (countryCode ile ayni "null=koru" deseni).
            if (req.notifyNews() != null) {
                existing.setNotifyNews(req.notifyNews());
            }
            // Kullanici↔cihaz bagi: JWT geldiyse (giris yapmis) guncelle.
            // null ise KORU (countryCode ile ayni "null=koru" deseni) —
            // token yenileme sirasinda gecici anonim register mevcut bagi
            // silmesin.
            if (appUserId != null) {
                existing.setAppUserId(appUserId);
            }
            existing.setLastSeenAt(Instant.now());
            repository.save(existing);
            log.debug("Device token guncellendi: id={} platform={} country={}",
                    existing.getId(), platform, existing.getCountryCode());
            return new DeviceTokenResponse(existing.getId(), false);
        }

        MobileDeviceToken fresh = new MobileDeviceToken();
        fresh.setFcmToken(token);
        fresh.setPlatform(platform);
        fresh.setAppVersion(req.appVersion());
        fresh.setLocale(locale);
        fresh.setCountryCode(countryCode);
        fresh.setAppUserId(appUserId);
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

    /**
     * Master "Tum bildirimleri kapat/ac" toggle. Mobile Profil ekrani
     * cagirir. Token bulunamazsa false doner — caller 404 yapabilir.
     */
    @Transactional
    public boolean setNotificationsEnabled(String fcmToken, boolean enabled) {
        MobileDeviceToken existing =
                repository.findByFcmToken(fcmToken.trim()).orElse(null);
        if (existing == null) {
            log.debug("setNotificationsEnabled: token bulunamadi {}", fcmToken);
            return false;
        }
        existing.setNotificationsEnabled(enabled);
        existing.setLastSeenAt(Instant.now());
        repository.save(existing);
        log.info("Device {} notifications {}",
                existing.getId(), enabled ? "ACILDI" : "KAPATILDI");
        return true;
    }

    /**
     * FIFA + UEFA Ulke siralama bildirimleri toggle. Mobile Ayarlar ekrani
     * cagirir. Token bulunamazsa false doner — caller 404 yapabilir.
     */
    @Transactional
    public boolean setRankingsCountryEnabled(String fcmToken, boolean enabled) {
        MobileDeviceToken existing =
                repository.findByFcmToken(fcmToken.trim()).orElse(null);
        if (existing == null) {
            log.debug("setRankingsCountryEnabled: token bulunamadi {}", fcmToken);
            return false;
        }
        existing.setNotifyRankingsCountry(enabled);
        existing.setLastSeenAt(Instant.now());
        repository.save(existing);
        log.info("Device {} ranking-country bildirimi {}",
                existing.getId(), enabled ? "ACILDI" : "KAPATILDI");
        return true;
    }

    /** Ulke kodunu normalize eder: trim + upper-case; bos ise null. */
    private static String normalizeCountry(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}

package com.scorestv.mobile.web;

import com.scorestv.mobile.service.MobileDeviceTokenService;
import com.scorestv.mobile.web.dto.DeviceTokenResponse;
import com.scorestv.mobile.web.dto.RegisterDeviceTokenRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile FCM device token register/update endpoint.
 *
 * <p>Anonim cihaz tabanli — kullanici hesabi yok. Her cihaz her app
 * acilisinda bu endpoint'i cagirir; yeni token INSERT, mevcut token
 * lastSeenAt + locale + appVersion UPDATE.
 *
 * <p>Public — JWT gerekmez (mobile zaten anonim). Rate limit gelecekte
 * eklenir (IP-bazli abuse kontrolu).
 */
@RestController
@RequestMapping("/api/v1/mobile/device-tokens")
public class MobileDeviceTokenController {

    private final MobileDeviceTokenService service;

    public MobileDeviceTokenController(MobileDeviceTokenService service) {
        this.service = service;
    }

    @PostMapping
    public DeviceTokenResponse register(
            @Valid @RequestBody RegisterDeviceTokenRequest req) {
        return service.registerOrUpdate(req);
    }

    /**
     * Master "Tum bildirimleri kapat/ac" toggle.
     *
     * <p>Mobile Profil > "Bildirimler" satirindan cagrilir.
     * {@code enabled=false} → NotificationDispatcher push gondermez.
     */
    @PatchMapping("/notifications-enabled")
    public ResponseEntity<Void> setNotificationsEnabled(
            @RequestParam String fcmToken,
            @RequestParam boolean enabled) {
        boolean updated = service.setNotificationsEnabled(fcmToken, enabled);
        return updated ? ResponseEntity.noContent().build()
                       : ResponseEntity.notFound().build();
    }

    /**
     * FIFA + UEFA Ulke siralama bildirimleri toggle.
     *
     * <p>Mobile Ayarlar > "Sıralama bildirimleri" satirindan cagrilir.
     * {@code enabled=false} → ulkeye gore siralama bildirimi gonderilmez.
     */
    @PatchMapping("/rankings-country-enabled")
    public ResponseEntity<Void> setRankingsCountryEnabled(
            @RequestParam String fcmToken,
            @RequestParam boolean enabled) {
        boolean updated = service.setRankingsCountryEnabled(fcmToken, enabled);
        return updated ? ResponseEntity.noContent().build()
                       : ResponseEntity.notFound().build();
    }
}

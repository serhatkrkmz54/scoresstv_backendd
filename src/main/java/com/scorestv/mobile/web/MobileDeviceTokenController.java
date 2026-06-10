package com.scorestv.mobile.web;

import com.scorestv.mobile.service.MobileDeviceTokenService;
import com.scorestv.mobile.web.dto.DeviceTokenResponse;
import com.scorestv.mobile.web.dto.RegisterDeviceTokenRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

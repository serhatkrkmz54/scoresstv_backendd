package com.scorestv.mobile.web;

import com.scorestv.mobile.service.MobileBasketballNotificationPrefsService;
import com.scorestv.mobile.web.dto.BasketballNotificationPrefsDto;
import com.scorestv.mobile.web.dto.SyncBasketballNotificationPrefsRequest;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mobile BASKETBOL bildirim tercihleri sync endpoint'leri.
 *
 * <p>Futbol {@link MobileNotificationPrefsController} ile aynı kontrat:
 * {@code PUT} replace, {@code GET} restore. fcmToken kullaniciyi identifies.
 */
@RestController
@RequestMapping("/api/v1/mobile/basketball/notification-prefs")
public class MobileBasketballNotificationPrefsController {

    private final MobileBasketballNotificationPrefsService service;

    public MobileBasketballNotificationPrefsController(
            MobileBasketballNotificationPrefsService service) {
        this.service = service;
    }

    @PutMapping
    public SyncNotificationPrefsResponse syncAll(
            @Valid @RequestBody SyncBasketballNotificationPrefsRequest req) {
        return service.syncAll(req);
    }

    @GetMapping
    public Map<Long, BasketballNotificationPrefsDto> getAll(
            @RequestParam String fcmToken) {
        return service.getAll(fcmToken);
    }
}

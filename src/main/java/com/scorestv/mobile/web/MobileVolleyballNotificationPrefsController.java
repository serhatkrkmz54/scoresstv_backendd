package com.scorestv.mobile.web;

import com.scorestv.mobile.service.MobileVolleyballNotificationPrefsService;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsResponse;
import com.scorestv.mobile.web.dto.SyncVolleyballNotificationPrefsRequest;
import com.scorestv.mobile.web.dto.VolleyballNotificationPrefsDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mobile VOLEYBOL bildirim tercihleri sync endpoint'leri.
 *
 * <p>Basketbol {@link MobileBasketballNotificationPrefsController} ile ayni
 * kontrat: {@code PUT} replace, {@code GET} restore. fcmToken kullaniciyi
 * identifies.
 */
@RestController
@RequestMapping("/api/v1/mobile/volleyball/notification-prefs")
public class MobileVolleyballNotificationPrefsController {

    private final MobileVolleyballNotificationPrefsService service;

    public MobileVolleyballNotificationPrefsController(
            MobileVolleyballNotificationPrefsService service) {
        this.service = service;
    }

    @PutMapping
    public SyncNotificationPrefsResponse syncAll(
            @Valid @RequestBody SyncVolleyballNotificationPrefsRequest req) {
        return service.syncAll(req);
    }

    @GetMapping
    public Map<Long, VolleyballNotificationPrefsDto> getAll(
            @RequestParam String fcmToken) {
        return service.getAll(fcmToken);
    }
}

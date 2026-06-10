package com.scorestv.mobile.web;

import com.scorestv.mobile.service.MobileNotificationPrefsService;
import com.scorestv.mobile.web.dto.NotificationPrefsDto;
import com.scorestv.mobile.web.dto.SyncNotificationPrefsRequest;
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
 * Mobile bildirim tercihleri sync endpoint'leri.
 *
 * <p>{@link #syncAll} — mobile tum prefs haritasini gonderir, backend REPLACE.
 * {@link #getAll} — restore icin (yeni cihaz, eski state'i geri yukle).
 *
 * <p>Public — fcmToken kullaniciyi identifies; baska auth gerekmez.
 */
@RestController
@RequestMapping("/api/v1/mobile/notification-prefs")
public class MobileNotificationPrefsController {

    private final MobileNotificationPrefsService service;

    public MobileNotificationPrefsController(MobileNotificationPrefsService service) {
        this.service = service;
    }

    /** Tum prefs'i replace eder (mobile state of truth). */
    @PutMapping
    public SyncNotificationPrefsResponse syncAll(
            @Valid @RequestBody SyncNotificationPrefsRequest req) {
        return service.syncAll(req);
    }

    /** Mevcut prefs'i cihaz icin dondur (debug/restore). */
    @GetMapping
    public Map<Long, NotificationPrefsDto> getAll(@RequestParam String fcmToken) {
        return service.getAll(fcmToken);
    }
}

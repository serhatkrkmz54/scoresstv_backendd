package com.scorestv.mobile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Mobile bildirim tercihlerini backend'e batch sync etme istegi.
 *
 * <p>Tum harita gonderilir; backend REPLACE eder (mobile state of truth).
 * Yani map'te olmayan takim varsa eski kaydi silinir.
 */
public record SyncNotificationPrefsRequest(
        @NotBlank
        String fcmToken,
        /** team_id → prefs haritasi. Bos olabilir (kullanici tum takimlari sildi). */
        @NotNull
        Map<Long, NotificationPrefsDto> prefs
) {}

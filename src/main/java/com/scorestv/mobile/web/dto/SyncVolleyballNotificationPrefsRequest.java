package com.scorestv.mobile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Voleybol bildirim tercihlerini backend'e batch sync etme istegi.
 *
 * <p>Basketbol {@link SyncBasketballNotificationPrefsRequest} ile ayni semantik
 * — tum harita gonderilir, backend REPLACE eder.
 */
public record SyncVolleyballNotificationPrefsRequest(
        @NotBlank
        String fcmToken,
        /** team_id → prefs haritasi. Bos olabilir. */
        @NotNull
        Map<Long, VolleyballNotificationPrefsDto> prefs
) {}

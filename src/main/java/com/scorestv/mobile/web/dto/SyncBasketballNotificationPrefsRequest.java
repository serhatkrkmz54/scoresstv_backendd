package com.scorestv.mobile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Basketbol bildirim tercihlerini backend'e batch sync etme isteği.
 *
 * <p>Futbol {@link SyncNotificationPrefsRequest} ile aynı semantik — tüm harita
 * gönderilir, backend REPLACE eder.
 */
public record SyncBasketballNotificationPrefsRequest(
        @NotBlank
        String fcmToken,
        /** team_id → prefs haritası. Boş olabilir (kullanıcı tüm basketbol takımlarını sildi). */
        @NotNull
        Map<Long, BasketballNotificationPrefsDto> prefs
) {}

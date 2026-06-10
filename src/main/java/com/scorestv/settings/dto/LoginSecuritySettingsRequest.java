package com.scorestv.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Brute-force koruma ayarlari: kac hatali denemeden sonra,
 * kac dakika kilit uygulanacagi.
 */
public record LoginSecuritySettingsRequest(

        @Min(value = 1, message = "En az 1 olmali")
        @Max(value = 20, message = "En fazla 20 olabilir")
        int maxFailedAttempts,

        @Min(value = 1, message = "En az 1 dakika olmali")
        @Max(value = 1440, message = "En fazla 1440 dakika (24 saat) olabilir")
        int lockoutMinutes
) {}

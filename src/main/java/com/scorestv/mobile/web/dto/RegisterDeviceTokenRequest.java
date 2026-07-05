package com.scorestv.mobile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Mobile uygulamadan FCM token kayit istegi.
 *
 * <p>Her app acilisinda gonderilebilir — backend INSERT/UPDATE yapar
 * (anonim cihaz tabanli, kullanici hesabi yok).
 */
public record RegisterDeviceTokenRequest(
        @NotBlank
        String fcmToken,
        /** "android" | "ios" — gelecekte "web" eklenebilir. */
        @NotBlank
        @Pattern(regexp = "^(android|ios)$")
        String platform,
        /** Sematik versiyon — debug icin yararli, opsiyonel. */
        String appVersion,
        /** "tr" | "en" — bildirim icerigi bu dilde uretilir. Default "tr". */
        String locale,
        /**
         * Cihaz ulke kodu — ISO-3 / futbol federasyon kodu (orn. "TUR",
         * "ENG"). FIFA + UEFA Ulke siralama bildirimleri icin hedefleme.
         * Opsiyonel; null/bos gelirse mevcut deger korunur (eski app surumu).
         */
        String countryCode,
        /**
         * Haber (news) push bildirimleri toggle. Opsiyonel — null gelirse
         * mevcut deger korunur (eski app surumu; entity default TRUE). Mobil
         * "Haber bildirimleri" ayari bu alani gonderir; false ise cihaza
         * yayin push'i gonderilmez. countryCode ile ayni "null=koru" deseni.
         */
        Boolean notifyNews
) {}

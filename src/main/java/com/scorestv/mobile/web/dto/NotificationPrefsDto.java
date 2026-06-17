package com.scorestv.mobile.web.dto;

/**
 * Bir takim icin bildirim olay tiplerinde acik/kapali bayraklari.
 *
 * <p>Anahtarlar mobile (Flutter) tarafindaki design ile uyumlu — Turkce
 * snake_case (gol, kirmizi, penalti, basladi, bitti, kadro) UI'da tanimli;
 * burada record alan adlari ayni kalir. Backend entity'de daha aciklayici
 * (notifyGoal, notifyRedCard, vs.) — mapping service katmaninda.
 *
 * <p>{@code kadro} (İlk 11 açıklandı) GERIYE-UYUMLU: nullable. Eski mobil
 * client'lar bu alani gondermez → null gelir, servis o zaman dokunmaz (entity
 * default'u/mevcut deger korunur).
 */
public record NotificationPrefsDto(
        boolean gol,
        boolean kirmizi,
        boolean penalti,
        boolean basladi,
        boolean bitti,
        Boolean kadro
) {

    /** Tum bildirimler acik default — yeni eklenen takimlar icin. */
    public static NotificationPrefsDto allEnabled() {
        return new NotificationPrefsDto(true, true, true, true, true, true);
    }
}

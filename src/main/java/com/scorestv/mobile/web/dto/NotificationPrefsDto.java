package com.scorestv.mobile.web.dto;

/**
 * Bir takim icin 5 olay tipinde bildirim acik/kapali bayraklari.
 *
 * <p>Anahtarlar mobile (Flutter) tarafindaki design ile uyumlu — Turkce
 * snake_case (gol, kirmizi, penalti, basladi, bitti) UI'da tanimli; burada
 * record alan adlari ayni kalir. Backend entity'de daha aciklayici
 * (notifyGoal, notifyRedCard, vs.) — mapping service katmaninda.
 */
public record NotificationPrefsDto(
        boolean gol,
        boolean kirmizi,
        boolean penalti,
        boolean basladi,
        boolean bitti
) {

    /** Tum bildirimler acik default — yeni eklenen takimlar icin. */
    public static NotificationPrefsDto allEnabled() {
        return new NotificationPrefsDto(true, true, true, true, true);
    }
}

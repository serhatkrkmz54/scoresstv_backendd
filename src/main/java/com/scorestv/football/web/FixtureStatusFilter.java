package com.scorestv.football.web;

import java.util.Locale;

/**
 * Anasayfada chip ile seçilen fikstür filtresi.
 *
 * <p>URL parametresi olarak {@code ?status=all|live|upcoming|finished} alınır.
 * Bilinmeyen ya da boş değer için {@link #ALL} kullanılır.
 */
public enum FixtureStatusFilter {

    /** Tüm fikstürler — varsayılan. */
    ALL,
    /** Sadece şu an oynanan maçlar (1H/HT/2H/ET/BT/P/LIVE). */
    LIVE,
    /** Henüz başlamamış maçlar (NS/TBD). */
    UPCOMING,
    /** Bitmiş, ertelenmiş, iptal edilmiş — yani aktif olmayan kalan her şey. */
    FINISHED;

    /**
     * Query parametresini enum'a çevirir. Büyük/küçük harf duyarsız;
     * boş ya da bilinmeyen değer {@link #ALL}'a düşer.
     */
    public static FixtureStatusFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}

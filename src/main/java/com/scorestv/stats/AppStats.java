package com.scorestv.stats;

import java.io.Serializable;
import java.util.List;

/**
 * Panel "Uygulama Istatistikleri" — kendi veritabanimizdan turetilen KPI'lar.
 * Firebase Analytics'ten farkli olarak KESIN + gecikmesiz (uye/cihaz/oyun
 * sayilari birinci-taraf veriden). Tek admin ucundan
 * ({@code GET /api/v1/admin/stats/app}) doner.
 */
public record AppStats(
        // ---- Uyeler (users tablosu) ----
        long usersTotal,
        long usersNew24h,
        long usersNew7d,
        long usersNew30d,
        long usersGoogle,
        long usersApple,
        long usersEmail,

        // ---- Cihazlar (mobile_device_tokens) ----
        long devicesTotal,
        long devicesAndroid,
        long devicesIos,
        long devicesNotifOn,
        long devicesLinked,     // app_user_id dolu (hesapla giris yapmis)
        long devicesActive7d,   // last_seen_at son 7 gun
        long devicesActive30d,
        List<CountryCount> topCountries,

        // ---- Oyun ----
        long gamePicksTotal,
        long gamePlayers        // tahmin yapan benzersiz kullanici sayisi
) implements Serializable {

    /** Ulke koduna gore cihaz sayisi (en cok N ulke). */
    public record CountryCount(String country, long count) implements Serializable {
    }
}

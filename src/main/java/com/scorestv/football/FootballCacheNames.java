package com.scorestv.football;

/**
 * API-Football verileri icin Redis cache adlari.
 *
 * <p>Her ad bir "tazelik katmanina" karsilik gelir; TTL'ler {@code RedisConfig}
 * icinde bu adlara gore ayarlanir. Katmanlar, dokumantasyondaki "Recommended
 * Calls" sikligindan turetilmistir; amac gunluk istek kotasi (orn. ucretsiz
 * planda 100 istek/gun) icinde kalmaktir.
 *
 * <p>Endpoint servisleri eklendikce {@code @Cacheable(value = ...)} icinde bu
 * sabitler kullanilir. Ornek:
 * <pre>{@code @Cacheable(value = FootballCacheNames.STATIC, key = "'leagues'")}</pre>
 */
public final class FootballCacheNames {

    private FootballCacheNames() {
    }

    /** Nadiren degisen referans veri: ulkeler, ligler, sezonlar (TTL 24 saat). */
    public static final String STATIC = "af-static";

    /** Gunluk tazelenen veri (TTL 6 saat). */
    public static final String DAILY = "af-daily";

    /**
     * Saatlik tazelenen veri: puan durumu, tahminler (TTL 60 dakika).
     * API-Football da bu uclari saatte bir gunceller.
     */
    public static final String HOURLY = "af-hourly";

    /**
     * Sik tazelenen veri: canli skorlar, fikstur listeleri, mac detayi
     * (TTL 15 saniye). API-Football'un kendi guncelleme cadence'i ile birebir.
     */
    public static final String LIVE = "af-live";

    /**
     * Kadro verisi (TTL 15 dakika). API-Football lineups ucu her 15 dk'da
     * bir guncellenir, daha kisa cache anlamsiz.
     */
    public static final String LINEUPS = "af-lineups";

    /**
     * FIFA + UEFA siralamalari icin ayri cache (TTL 6 saat). Sync sonrasi
     * @CacheEvict ile bosaltilir; STATIC ile karistirilmamasi referans
     * verilerini etkilemez.
     */
    public static final String RANKINGS = "af-rankings";
}

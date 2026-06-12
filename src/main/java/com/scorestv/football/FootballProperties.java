package com.scorestv.football;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml içindeki "scorestv.football.*" ayarları — futbol verisi
 * senkronuna dair tip-güvenli yapılandırma.
 *
 * <p>{@code @DefaultValue} sayesinde yml'de hiçbir değer verilmese bile makul
 * varsayılanlarla çalışır.
 */
@ConfigurationProperties(prefix = "scorestv.football")
public record FootballProperties(
        @DefaultValue Sync sync,
        @DefaultValue Serving serving
) {

    /**
     * Görüntüleme/sunum ayarları — anasayfa lig sıralaması gibi UX
     * kararları burada toplanır.
     */
    public record Serving(
            /**
             * Anasayfa lig listesinin en başında BU SIRAYLA gösterilecek
             * API-Football lig ID'leri. Listedeki sıra önceliktir. Geri
             * kalan ligler ülke adına göre alfabetik, aynı ülke içinde
             * lig adına göre alfabetik sıralanır.
             *
             * <p>Yapılandırma: {@code application.yml} →
             * {@code scorestv.football.serving.featured-league-ids}.
             * Liste boş olduğunda hiçbir lig öncelikli sayılmaz —
             * tamamen alfabetik düzene düşer.
             */
            @DefaultValue({}) List<Long> featuredLeagueIds,

            /**
             * Sol ray "Popüler Ligler" listesi — BU SIRAYLA gösterilecek
             * API-Football lig ID'leri. Anasayfa fikstür sıralamasından
             * (featuredLeagueIds) bağımsızdır; tamamen elle seçilir.
             *
             * <p>Yapılandırma: {@code application.yml} →
             * {@code scorestv.football.serving.popular-league-ids}.
             * Boşsa sol rayda popüler ligler bölümü görünmez.
             */
            @DefaultValue({}) List<Long> popularLeagueIds,

            /**
             * Sol ray "Ülkeler" listesi — BU SIRAYLA gösterilecek ülke ID'leri.
             * {@code application.yml} → {@code scorestv.football.serving.popular-country-ids}.
             * Boşsa sol rayda ülkeler bölümü görünmez.
             */
            @DefaultValue({}) List<Long> popularCountryIds,

            /**
             * Sol ray "Ülkeler" (milli takımlar) listesi — BU SIRAYLA gösterilecek
             * takım ID'leri. Ülkeler sistemde takım (national=true) olarak tutulur.
             * {@code application.yml} → {@code scorestv.football.serving.popular-team-ids}.
             */
            @DefaultValue({}) List<Long> popularTeamIds
    ) {
    }


    /** Zamanlanmış senkron işlerinin ayarları. */
    public record Sync(
            /** Zamanlanmış senkron işleri çalışsın mı? Dev'de kapalı tutulur. */
            @DefaultValue("false") boolean enabled,

            /** Anasayfa penceresi: bugünden kaç gün öncesi dahil. */
            @DefaultValue("7") int windowDaysBefore,

            /** Anasayfa penceresi: bugünden kaç gün sonrası dahil. */
            @DefaultValue("7") int windowDaysAfter,

            /** "Bugün" tanımının yapıldığı saat dilimi (gün hesabı için). */
            @DefaultValue("Europe/Istanbul") String timezone,

            /** Tam pencere senkronu için cron ifadesi (Spring 6 alanlı). */
            @DefaultValue("0 0 4 * * *") String windowCron,

            /** Referans veri (ülke/lig/sezon) senkronu için cron ifadesi. */
            @DefaultValue("0 0 5 * * SUN") String referenceCron,

            /**
             * Bugünü saatlik tazeleyen iş için cron ifadesi. Alt liglerin
             * gecikmeli NS→FT/PST/CANC güncellemelerini en geç 1 saat içinde
             * yakalar; canlı ticker (yalnız aktif maçlar) ile günlük pencere
             * senkronu (04:00) arasındaki boşluğu kapatır.
             */
            @DefaultValue("0 0 * * * *") String todayRefreshCron,

            /**
             * Canlı maç senkronu (GET /fixtures?live=all) çalışsın mı? Bu açık
             * olduğunda zamanlanmış iş periyodik olarak API'yi yoklar ve
             * değişimleri WebSocket üzerinden yayar. Dev'de kapalı tutulur.
             */
            @DefaultValue("false") boolean liveEnabled,

            /**
             * Canlı senkron çağrıları arasındaki süre (saniye). API-Football'un
             * önerdiği alt sınır 15 saniyedir.
             */
            @DefaultValue("15") int liveIntervalSeconds,

            /**
             * Yaklaşan-maç kadro yoklayıcının çalışma aralığı (dakika).
             * Bu iş önümüzdeki {@code lineupsLookaheadHours} saat içinde başlayacak
             * ve henüz kadrosu açıklanmamış maçlar için {@code /fixtures/lineups}
             * çağrısı yapar. API'nin önerdiği polling: 15 dk.
             */
            @DefaultValue("15") int imminentLineupsIntervalMinutes,

            /**
             * Yaklaşan-maç poller'ının ne kadar ileriyi taradığı (saat).
             * 2 saat = kickoff'a 20-40 dk kala API'nin kadroyu yayınladığı pencereyi
             * rahatça kapsar.
             */
            @DefaultValue("2") int lineupsLookaheadHours,

            /**
             * Canlı istatistik tazeleme aralığı (saniye). API-Football'un önerdiği
             * polling: aktif maçlar için 1 dk. Yoğun pikte kota tüketimini
             * düşürmek istersen 90/120 yapabilirsin.
             */
            @DefaultValue("60") int liveStatisticsIntervalSeconds,

            /**
             * Canlı oyuncu istatistik tazeleme aralığı (saniye). Player rating'i
             * skor kadar kritik değildir; varsayılan 120 sn ile kotayı korur.
             */
            @DefaultValue("120") int livePlayerStatsIntervalSeconds,

            /**
             * "Covered" işaretli olmayan (alt ligler) maçlar için canlı sync
             * aralıklarına uygulanacak çarpan. 4 = non-covered için events 30→120sn,
             * stats 60→240sn, player_stats 120→480sn. Pik kota baskısını yumuşatır.
             */
            @DefaultValue("4") int nonCoveredRateMultiplier
    ) {

        /** Penceredeki toplam gün sayısı (öncesi + bugün + sonrası). */
        public int windowSize() {
            return windowDaysBefore + 1 + windowDaysAfter;
        }
    }
}

package com.scorestv.basketball;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Basketbol (API-Basketball v1) entegrasyonu yapılandırması.
 *
 * <p>Football'dan TAMAMEN ayrıdır — kendi base-url'i, kendi flag'leri. Yalnızca
 * API anahtarı paylaşılır ({@code apiKey} football ile aynı API-Sports key'i).
 *
 * <p>{@code enabled=false} ise basketbol sync/job'ları hiç çalışmaz.
 */
@ConfigurationProperties(prefix = "scorestv.basketball")
public record BasketballProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://v1.basketball.api-sports.io") String baseUrl,
        /** API-Sports anahtarı — football ile AYNI key. */
        String apiKey,
        @DefaultValue("400") int requestsPerMinute,
        @DefaultValue("Europe/Istanbul") String timezone,
        /** Canlı skor sync açık mı (periyodik /games?live=all). */
        @DefaultValue("false") boolean liveEnabled,
        @DefaultValue("20") int liveIntervalSeconds,
        /** Bugün/yarın fikstür yenileme cron (6 alanlı Spring cron). */
        @DefaultValue("0 */30 * * * *") String todayCron,
        /** Kayan pencere: bugünden GERİYE kaç gün (geçmiş sonuçlar). */
        @DefaultValue("7") int windowDaysBefore,
        /** Kayan pencere: bugünden İLERİ kaç gün (gelecek fikstür). */
        @DefaultValue("7") int windowDaysAfter,
        /** Tüm pencereyi (±gün) tarayan günlük cron. */
        @DefaultValue("0 0 4 * * *") String windowCron,
        /** Görüntüleme/sunum ayarları — sol ray popüler ligler/takımlar. */
        @DefaultValue Serving serving
) {

    /**
     * Sunum ayarları — futbol {@code FootballProperties.Serving} esi. Sol ray
     * "Popüler Ligler" ve "Popüler Takımlar" listeleri config'ten elle seçilir.
     */
    public record Serving(
            /**
             * Sol ray "Popüler Ligler" — BU SIRAYLA gösterilecek API-Basketball
             * lig ID'leri. Liste boşsa sol rayda popüler ligler bölümü görünmez
             * (TÜM liglere düşmez — elle seçim şarttır).
             *
             * <p>Yapılandırma: {@code application.yml} →
             * {@code scorestv.basketball.serving.popular-league-ids}
             * (env: {@code BASKETBALL_POPULAR_LEAGUE_IDS}).
             */
            @DefaultValue({}) List<Long> popularLeagueIds,

            /**
             * Sol ray "Popüler Takımlar" — BU SIRAYLA gösterilecek API-Basketball
             * takım ID'leri. Boşsa sol rayda takımlar bölümü görünmez.
             *
             * <p>Yapılandırma: {@code application.yml} →
             * {@code scorestv.basketball.serving.popular-team-ids}
             * (env: {@code BASKETBALL_POPULAR_TEAM_IDS}).
             */
            @DefaultValue({}) List<Long> popularTeamIds
    ) {
    }
}

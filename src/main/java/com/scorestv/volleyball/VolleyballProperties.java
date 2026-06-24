package com.scorestv.volleyball;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Voleybol (API-Volleyball v1) entegrasyonu yapilandirmasi.
 *
 * <p>Football/basketball'dan TAMAMEN ayridir — kendi base-url'i, kendi
 * flag'leri. Yalnizca API anahtari paylasilir ({@code apiKey} football ile
 * ayni API-Sports key'i).
 *
 * <p>{@code enabled=false} ise voleybol sync/job'lari hic calismaz.
 */
@ConfigurationProperties(prefix = "scorestv.volleyball")
public record VolleyballProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://v1.volleyball.api-sports.io") String baseUrl,
        /** API-Sports anahtari — football ile AYNI key. */
        String apiKey,
        @DefaultValue("450") int requestsPerMinute,
        @DefaultValue("Europe/Istanbul") String timezone,
        /** Canli skor sync acik mi (periyodik bugun cekimi). */
        @DefaultValue("false") boolean liveEnabled,
        @DefaultValue("20") int liveIntervalSeconds,
        /** Bugun/yarin fikstur yenileme cron (6 alanli Spring cron). */
        @DefaultValue("0 */30 * * * *") String todayCron,
        /** Kayan pencere: bugunden GERIYE kac gun (gecmis sonuclar). */
        @DefaultValue("7") int windowDaysBefore,
        /** Kayan pencere: bugunden ILERI kac gun (gelecek fikstur). */
        @DefaultValue("7") int windowDaysAfter,
        /** Tum pencereyi (±gun) tarayan gunluk cron. */
        @DefaultValue("0 0 4 * * *") String windowCron,
        /** Ulke + lig referans seed cron (haftalik). */
        @DefaultValue("0 0 3 * * 1") String referenceCron,
        /** Logo/bayrak aynalama (CDN) cron. */
        @DefaultValue("0 15 * * * *") String imageMirrorCron,
        /** Takim kadrosu (lig+sezon junction) cron. */
        @DefaultValue("0 30 4 * * *") String teamSyncCron,
        /** Serving (one-cikan ligler vs.) yapilandirmasi. */
        @DefaultValue Serving serving
) {

    /**
     * Serving (mobile/web sunum) ayarlari.
     *
     * @param featuredLeagueIds one cikan voleybol lig id'leri (su an bos liste).
     */
    public record Serving(
            @DefaultValue List<Long> featuredLeagueIds
    ) {}
}

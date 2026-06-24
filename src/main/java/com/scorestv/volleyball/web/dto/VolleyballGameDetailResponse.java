package com.scorestv.volleyball.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Voleybol mac detay sayfasinin yaniti — basketbol
 * {@code BasketballGameDetailResponse}'in voleybol esi, LEANER.
 *
 * <p>Voleybolda oyuncu/mac-bazli-takim istatistigi YOK; bu yuzden DTO
 * yalnizca: hero score (set + per-set sayilar) + standings + h2h + sezonluk
 * takim istatistikleri icerir.
 */
public record VolleyballGameDetailResponse(
        Long id,
        /** SEO slug — frontend URL'i ile ayni. */
        String slug,
        /** Stage/round; dil bilincli. */
        String stage,
        /** Hafta etiketi; null olabilir. */
        String week,
        Instant kickoff,
        Instant lastSyncedAt,
        Status status,
        TeamRef homeTeam,
        TeamRef awayTeam,
        ScoreBreakdown score,
        LeagueRef league,
        /**
         * Iki takimin sezonluk istatistikleri — 0, 1 veya 2 eleman.
         * (lig+sezon icin /teams/statistics)
         */
        List<TeamSeasonStatsView> teamStats,
        /**
         * Iki takim arasi gecmis maclar — yeni → eski, en fazla 10 mac.
         */
        List<H2hGameView> headToHead,
        /**
         * Bu macin liginin sezonluk puan durumu — gruplara gore.
         */
        List<StandingsGroup> standings,
        SeoBundle seo
) implements Serializable {

    /** Mac durumu. */
    public record Status(
            /** Kisa kod: NS / S1..S5 / FT / AW / POST / CANC ... */
            String shortName,
            String longName,
            /** Dile cevrilmis kisa label. */
            String statusText
    ) implements Serializable {}

    /** Takim referansi — slug, logo, isim. */
    public record TeamRef(
            Long id,
            String name,
            String displayName,
            String logo,
            String slug
    ) implements Serializable {}

    /**
     * Skor dagilimi — kazanilan set sayisi (homeSets/awaySets) +
     * her set icin per-set sayilar (sets listesi).
     */
    public record ScoreBreakdown(
            Integer homeSets,
            Integer awaySets,
            /** Set bazli sayilar — index 0 = 1. set. Sadece oynanan setler. */
            List<SetScore> sets
    ) implements Serializable {}

    /** Tek setin home/away sayilari. */
    public record SetScore(
            Integer setNumber,
            Integer home,
            Integer away
    ) implements Serializable {}

    /** Lig referansi. */
    public record LeagueRef(
            Long id,
            String name,
            String type,
            String slug,
            String logo,
            String countryName,
            String countryFlag,
            String season
    ) implements Serializable {}

    /** Bir takimin sezonluk ozet istatistikleri. */
    public record TeamSeasonStatsView(
            TeamRef team,
            Integer gamesPlayed,
            Integer wins,
            Integer loses,
            String winPercentage,
            Integer setsForTotal,
            Double setsForAvg,
            Integer setsAgainstTotal,
            Double setsAgainstAvg,
            String form
    ) implements Serializable {}

    /** H2H tek mac satiri. */
    public record H2hGameView(
            Long id,
            String slug,
            Instant kickoff,
            String statusShort,
            String statusText,
            TeamRef homeTeam,
            TeamRef awayTeam,
            Integer homeSets,
            Integer awaySets,
            String winnerSide   // "home" / "away" / null
    ) implements Serializable {}

    /** Bir grup standings. */
    public record StandingsGroup(
            String groupName,
            String stage,
            List<StandingRow> rows
    ) implements Serializable {}

    public record StandingRow(
            Integer position,
            TeamRef team,
            Integer gamesPlayed,
            Integer won,
            Integer lost,
            String winPercentage,
            Integer setsFor,
            Integer setsAgainst,
            Integer setsDifference,
            Integer points,
            String form,
            String description
    ) implements Serializable {}

    /** SEO paketi — basketbol SeoBundle esi (futbol MatchSeoResponse esi). */
    public record SeoBundle(
            String title,
            String description,
            /** Canonical URL (locale'a uygun host + slug). */
            String canonical,
            /** Open Graph + Twitter ortak baslik. */
            String ogTitle,
            String ogDescription,
            String ogImage,
            /** JSON-LD structured data (SportsEvent schema.org). */
            String jsonLd,
            /** Breadcrumbs JSON-LD ayri. */
            String breadcrumbsJsonLd,
            /** TR/EN hreflang alternatif URL'leri. */
            List<HreflangAlt> hreflang
    ) implements Serializable {}

    public record HreflangAlt(String lang, String url) implements Serializable {}
}

package com.scorestv.basketball.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Basketbol mac detay sayfasinin yaniti — futbol {@code MatchDetailResponse}'in
 * basketbol esi.
 *
 * <p>SADECE veri tasiyici (Cacheable @JsonSerialize hedefi). Eklenmesi muhtemel
 * alanlar (broadcasts, odds, bracket) sonradan eklenir — backward compatible.
 *
 * <p>Sub-DTO'lar bu dosyada inline nested record olarak tanimli ki tek import
 * yeter, paket karmasiklasmasin.
 */
public record BasketballGameDetailResponse(
        Long id,
        /** SEO slug — frontend URL'i ile ayni. */
        String slug,
        /** Stage/round (orn. "Regular Season"); dil bilincli. */
        String stage,
        /** Hafta etiketi (orn. "Week 12"); null olabilir. */
        String week,
        Instant kickoff,
        Instant lastSyncedAt,
        Status status,
        TeamRef homeTeam,
        TeamRef awayTeam,
        ScoreBreakdown score,
        LeagueRef league,
        /**
         * Takim istatistikleri — 0 veya 2 eleman. Mac baslamadiysa veya
         * istatistik henuz cekilmediyse bos liste.
         */
        List<TeamStatsView> teamStats,
        /**
         * Oyuncu istatistikleri — takim bazli gruplanmis. Her takim icin
         * starters + bench ayrik. Mac baslamadiysa bos liste.
         */
        List<PlayerStatGroup> playerStats,
        /**
         * Iki takim arasi gecmis maclar — yeni → eski, en fazla 10 mac.
         * Mevcut macin ID'si dislanir. DB'de yoksa bos doner.
         */
        List<H2hGameView> headToHead,
        /**
         * Bu macin liginin sezonluk puan durumu — gruplara gore. NBA gibi
         * liglerde "Western Conference"/"Eastern Conference" ayri eleman.
         * Tek-gruplu liglerde tek eleman.
         */
        List<StandingsGroup> standings,
        SeoBundle seo
) implements Serializable {

    // ============================================================
    // Sub-DTO'lar — nested record'lar
    // ============================================================

    /** Mac durumu — API'den + dile cevrilmis kisa label. */
    public record Status(
            /** Kisa kod: NS / Q1 / Q2 / Q3 / Q4 / OT / BT / HT / FT / AOT / POST / CANC ... */
            String shortName,
            /** Tam ad ("Game Finished", "In Play", ...) — cevrilmemis. */
            String longName,
            /** Oyun saati "5:23" veya kalan dk. Sadece in-play'de dolu. */
            String timer,
            /** Dile cevrilmis kisa label ("Devam ediyor", "Devre Arasi", ...). */
            String statusText
    ) implements Serializable {}

    /** Takim referansi — slug, logo, isim. */
    public record TeamRef(
            Long id,
            String name,
            /** TR/EN ad — locale'a gore secilmis. */
            String displayName,
            String logo,
            /** Web URL'inde kullanilacak slug ("denver-nuggets-7"). */
            String slug
    ) implements Serializable {}

    /** Skor dagilimi (Q1-Q4 + OT + total). */
    public record ScoreBreakdown(
            SidescoreLine home,
            SidescoreLine away
    ) implements Serializable {}

    public record SidescoreLine(
            Integer q1,
            Integer q2,
            Integer q3,
            Integer q4,
            Integer overTime,
            Integer total
    ) implements Serializable {}

    /** Lig referansi — slug, logo, ulke. */
    public record LeagueRef(
            Long id,
            String name,
            String type,
            String slug,
            String logo,
            String countryName,
            String countryFlag,
            /** "2023-2024" formati. */
            String season
    ) implements Serializable {}

    /** Mac basina takim istatistikleri — bir takim icin tum kategoriler. */
    public record TeamStatsView(
            TeamRef team,
            /** Field goals (2pt + 3pt birlesik). */
            MadeAttempt fieldGoals,
            /** 3-point. */
            MadeAttempt threepoint,
            /** Free throws. */
            MadeAttempt freethrows,
            Rebounds rebounds,
            Integer assists,
            Integer steals,
            Integer blocks,
            Integer turnovers,
            Integer personalFouls
    ) implements Serializable {}

    /** Total/attempts/percentage uclusu — FG/3PT/FT ortak. */
    public record MadeAttempt(Integer total, Integer attempts, String percentage)
            implements Serializable {}

    public record Rebounds(Integer total, Integer offence, Integer defense)
            implements Serializable {}

    /** Bir takimin tum oyuncu satirlari (starters + bench). */
    public record PlayerStatGroup(
            TeamRef team,
            List<PlayerStatRow> starters,
            List<PlayerStatRow> bench
    ) implements Serializable {}

    /** Tek oyuncu satiri. */
    public record PlayerStatRow(
            Long playerId,
            String playerName,
            /** "32:15" formati string. */
            String minutes,
            Integer points,
            MadeAttempt fieldGoals,
            MadeAttempt threepoint,
            MadeAttempt freethrows,
            Rebounds rebounds,
            Integer assists,
            Integer steals,
            Integer blocks,
            Integer turnovers,
            Integer personalFouls
    ) implements Serializable {}

    /** H2H tek mac satiri — minimum bilgi. */
    public record H2hGameView(
            Long id,
            String slug,
            Instant kickoff,
            String statusShort,
            String statusText,
            TeamRef homeTeam,
            TeamRef awayTeam,
            Integer homeTotal,
            Integer awayTotal,
            String winnerSide   // "home" / "away" / "draw" / null
    ) implements Serializable {}

    /** Bir grup standings (Western Conference, Group A, ...). */
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
            Integer pointsFor,
            Integer pointsAgainst,
            Integer pointsDifference,
            String form,
            String description,
            String descriptionText   // dile cevrilmis
    ) implements Serializable {}

    /** SEO paketi — futbol MatchSeoResponse esi. */
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

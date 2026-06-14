package com.scorestv.basketball.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Basketbol takim detay sayfasinin tum yanit DTO'su.
 *
 * <p>5 sekme verisini tek payload'da tasir: hero + overview (lastGame +
 * nextGame + sezon ozeti) + roster + fixtures + statistics + standings.
 * Lazy sync ile thin yanit dahi dolu hero ve "Yukleniyor" gostergesi
 * sergilemeye yeter.
 *
 * @param hero              ust seksiyon — logo + isim + ulke + venue
 * @param leagueRef         su anki sezon ligi (link icin)
 * @param selectedSeason    secili sezon string'i
 * @param availableSeasons  bu takim icin gosterilebilecek sezonlar (yeni->eski)
 * @param overview          ozet karti (son mac + sonraki mac + sezon ozet)
 * @param roster            mevcut sezon kadrosu
 * @param recentGames       son maclar (azalan)
 * @param upcomingGames     yaklasan maclar (artarak)
 * @param statistics        sezon istatistikleri bloku
 * @param standingsPosition takimin lig sirasi (highlight icin)
 * @param lastSyncedAt      en son tazeleme zamani (genel)
 */
public record BasketballTeamDetailResponse(
        TeamHero hero,
        LeagueRef leagueRef,
        String selectedSeason,
        List<String> availableSeasons,
        OverviewBlock overview,
        List<RosterPlayer> roster,
        List<FixtureItem> recentGames,
        List<FixtureItem> upcomingGames,
        StatisticsBlock statistics,
        StandingPosition standingsPosition,
        Instant lastSyncedAt
) {

    public record TeamHero(
            Long id,
            String name,
            String displayName,
            String logo,
            String code,
            Integer founded,
            Boolean national,
            String countryName,
            String countryCode,
            String countryFlag,
            String venueName,
            String venueCity,
            Integer venueCapacity,
            String slug
    ) {}

    public record LeagueRef(
            Long id,
            String name,
            String displayName,
            String logo,
            String slug,
            String type
    ) {}

    /** Genel sekmesinde gosterilen ozet karti. */
    public record OverviewBlock(
            FixtureItem lastGame,
            FixtureItem nextGame,
            SeasonSummary seasonSummary
    ) {}

    /**
     * Sezon ozet — Tab Genel'in alt karti. Statistics bloku tam dolarken,
     * Genel sekmesinde sadece bu kucuk kart gosterilir.
     */
    public record SeasonSummary(
            Integer wins,
            Integer loses,
            BigDecimal winPercentage,
            BigDecimal pointsForAvg,
            BigDecimal pointsAgainstAvg
    ) {}

    public record RosterPlayer(
            Long id,
            String name,
            String displayName,
            String photo,
            String position,
            Integer jerseyNumber,
            Integer heightCm,
            Integer weightKg,
            String nationality,
            String slug
    ) {}

    /** Bir fixture (gecmis veya gelecek). Maca tap edip detay sayfasina gider. */
    public record FixtureItem(
            Long id,
            String slug,
            Instant startAt,
            String statusShort,
            String statusLong,
            String statusText,
            String stage,
            String week,
            TeamRef home,
            TeamRef away,
            Integer homeScore,
            Integer awayScore
    ) {}

    public record TeamRef(
            Long id,
            String name,
            String displayName,
            String logo,
            String slug
    ) {}

    /** Istatistik sekmesinin DTO'su. */
    public record StatisticsBlock(
            Integer gamesPlayed,
            Integer wins,
            Integer loses,
            BigDecimal winPercentage,
            Integer pointsForTotal,
            BigDecimal pointsForAvg,
            Integer pointsAgainstTotal,
            BigDecimal pointsAgainstAvg,
            BigDecimal pointsDiffAvg,
            Integer longestWinStreak,
            Integer longestLoseStreak,
            String form,
            HomeAwayBlock homeBreakdown,
            HomeAwayBlock awayBreakdown
    ) {}

    /** Ev veya deplasman ozeti. */
    public record HomeAwayBlock(
            Integer played,
            Integer wins,
            Integer loses,
            BigDecimal winPercentage,
            BigDecimal pointsForAvg,
            BigDecimal pointsAgainstAvg
    ) {}

    /**
     * Takimin lig icindeki konumu — Puan Durumu sekmesinde takim satirini
     * vurgulamak icin. Tabloyu daha sonra mobile tarafta /standings/page
     * cagrisiyla cekeriz; bu blok yalnizca "su anda Nincisin?" widget'i besler.
     */
    public record StandingPosition(
            Integer position,
            String groupName,
            Integer gamesPlayed,
            Integer wins,
            Integer loses,
            BigDecimal winPercentage,
            String description
    ) {}
}

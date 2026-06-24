package com.scorestv.volleyball.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Voleybol takim detay sayfasi yaniti — basketbol team detail'in voleybol esi,
 * LEANER (oyuncu/kadro yok). Profil + sezonluk istatistikler + son/yaklasan
 * maclar + standings satiri.
 */
public record VolleyballTeamDetailResponse(
        Long id,
        String name,
        String displayName,
        String slug,
        String logo,
        String countryName,
        String countryFlag,
        boolean national,
        String season,
        Instant lastSyncedAt,
        SeasonStats seasonStats,
        List<GameRef> recentGames,
        List<GameRef> upcomingGames,
        List<StandingRow> standings
) implements Serializable {

    public record SeasonStats(
            Long leagueId,
            String leagueName,
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

    public record GameRef(
            Long id,
            String slug,
            Instant kickoff,
            String statusShort,
            String leagueName,
            TeamSide home,
            TeamSide away,
            Integer homeSets,
            Integer awaySets
    ) implements Serializable {}

    public record TeamSide(Long id, String name, String logo) implements Serializable {}

    public record StandingRow(
            Integer position,
            String groupName,
            Integer gamesPlayed,
            Integer won,
            Integer lost,
            Integer setsFor,
            Integer setsAgainst,
            Integer points,
            String form
    ) implements Serializable {}
}

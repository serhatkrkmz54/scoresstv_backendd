package com.scorestv.volleyball.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Voleybol lig puan durumu sayfasi yaniti — basketbol standings page'in
 * voleybol esi, LEANER.
 */
public record VolleyballStandingsPageResponse(
        Long leagueId,
        String leagueName,
        String leagueLogo,
        String season,
        /** Bu ligin dolu sezonlari (dropdown) — yeni → eski. */
        List<String> availableSeasons,
        List<Group> groups
) implements Serializable {

    public record Group(
            String groupName,
            String stage,
            List<Row> rows
    ) implements Serializable {}

    public record Row(
            Integer position,
            Long teamId,
            String teamName,
            String teamLogo,
            String teamSlug,
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
}

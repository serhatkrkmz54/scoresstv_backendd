package com.scorestv.basketball.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Basketbol puan durumu sayfasinin focused yaniti — futbol
 * {@code StandingsPageResponse} esi.
 *
 * <p>Lig hero (logo + name + country) + sezon listesi (dropdown) + grup-bazli
 * standings rows. NBA gibi liglerde {@link StandingsGroup} 8 grup
 * (Conference + Division) iceriebilir; tek-gruplu liglerde tek eleman.
 */
public record BasketballStandingsPageResponse(
        LeagueHero league,
        /** Suanki sezon — frontend default secimi. */
        String currentSeason,
        /** Tum sezonlar (dropdown icin) — yeni → eski. */
        List<String> availableSeasons,
        /** Grup-bazli standings. */
        List<StandingsGroup> groups,
        Instant lastSyncedAt
) implements Serializable {

    public record LeagueHero(
            Long id,
            String name,
            String displayName,
            String type,
            String slug,
            String logo,
            String countryName,
            String countryFlag
    ) implements Serializable {}

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
            String description
    ) implements Serializable {}

    public record TeamRef(
            Long id,
            String name,
            String displayName,
            String logo,
            String slug
    ) implements Serializable {}
}

package com.scorestv.rankings.web.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * UEFA Kulup Katsayisi yaniti.
 */
public record UefaClubRankingResponse(
        Integer totalClubs,
        Integer targetSeasonYear,
        Instant lastUpdated,
        List<Row> clubs
) implements Serializable {

    public record Row(
            Integer rank,
            String clubId,
            String clubName,
            String clubShortName,
            String clubOfficialName,
            String teamCode,
            String logoUrl,
            String bigLogoUrl,
            String mediumLogoUrl,
            String countryCode,
            String countryName,
            BigDecimal totalPoints,
            /** UP / DOWN / STABLE. */
            String trend,
            Integer numberOfMatches,
            Integer numberOfTeams,
            Integer baseSeasonYear,
            /**
             * Son 5 sezonun ayri puanlari — JSONB passthrough. Frontend
             * istedigi alani okur (seasonYear, totalPoints, position, ...).
             */
            List<Map<String, Object>> seasonRankings
    ) implements Serializable {}
}

package com.scorestv.rankings.web.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * UEFA Milli Takim Katsayisi yaniti.
 */
public record UefaCountryRankingResponse(
        Integer totalCountries,
        Integer targetSeasonYear,
        Instant lastUpdated,
        List<Row> countries
) implements Serializable {

    public record Row(
            Integer rank,
            String countryUefaId,
            String countryName,
            String countryCode,
            String logoUrl,
            String bigLogoUrl,
            String mediumLogoUrl,
            BigDecimal totalPoints,
            String trend,
            Integer numberOfMatches,
            Integer numberOfTeams,
            List<Map<String, Object>> seasonRankings
    ) implements Serializable {}
}

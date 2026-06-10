package com.scorestv.rankings.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * FIFA Erkek Milli Takim Siralamasi API yaniti.
 *
 * <p>Endpoint: {@code https://api.fifa.com/api/v3/fifarankings/rankings/live
 * ?gender=1&sportType=0&language=en}
 *
 * <p>API tum 211 ulkeyi tek istekte doner — pagination yok.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FifaRankingApiDto(
        List<Row> Results
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            String IdTeam,
            List<TeamName> TeamName,
            Integer Gender,
            String IdConfederation,
            Integer RankingMovement,
            String ConfederationName,
            String IdCountry,
            Integer RatedMatches,
            Integer Rank,
            Integer PrevRank,
            BigDecimal TotalPoints,
            BigDecimal PrevPoints
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamName(String Locale, String Description) {}
}

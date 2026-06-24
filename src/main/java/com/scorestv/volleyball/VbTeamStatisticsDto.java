package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code /teams/statistics?team=X&league=Y&season=Z} yanit yapisi.
 *
 * <p>Yanit TEK NESNE (liste degil). API ornek (defansif):
 * <pre>{
 *   "country": {...}, "league": {...}, "team": {...},
 *   "games": {
 *     "played": {"home": N, "away": N, "all": N},
 *     "wins":   {"home": {"total":N, "percentage":"0.5"}, "away":{...}, "all":{...}},
 *     "draws":  {"home": {...}, "away":{...}, "all":{...}},  // voleybolda hep 0
 *     "loses":  {"home": {...}, "away":{...}, "all":{...}}
 *   },
 *   "goals": {  // voleybolda "goals" = set/sayi
 *     "for":     {"total": {"home":N, "away":N, "all":N},
 *                 "average": {"home":"X.X", "away":"X.X", "all":"X.X"}},
 *     "against": {... ayni sema ...}
 *   }
 * }</pre>
 *
 * <p>Win/Lose percentage'leri String gelir (API formatti). Caller defansif
 * parse eder. Form bilgisi bu endpoint'te yoktur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbTeamStatisticsDto(
        VbTeamDto.Country country,
        Object league,
        VbTeamDto team,
        Games games,
        Goals goals
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Games(
            HomeAwayAll played,
            ResultBlock wins,
            ResultBlock draws,
            ResultBlock loses
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultBlock(
            WinLose home,
            WinLose away,
            WinLose all
    ) {}

    /** Win/lose icindeki bir hucre — total say + percentage (API string). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WinLose(
            Integer total,
            /** Genelde String "0.500" formatinda; defansif Object. */
            Object percentage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HomeAwayAll(
            Integer home,
            Integer away,
            Integer all
    ) {}

    /** Voleybolda "goals" = set/sayi for/against blogu. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(
            // 'for' Java keyword — Jackson @JsonProperty ile JSON anahtarini esle.
            @JsonProperty("for") ForAgainst forGoals,
            ForAgainst against
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForAgainst(
            HomeAwayAll total,
            HomeAwayAvg average
    ) {}

    /** Average API'de String "98.5" gelir; defansif Object. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HomeAwayAvg(
            Object home,
            Object away,
            Object all
    ) {}
}

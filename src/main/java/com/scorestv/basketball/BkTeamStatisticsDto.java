package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code /teams/statistics?team=X&league=Y&season=Z} yanit yapisi.
 *
 * <p>API ornek (cok karmasik, defansif Object):
 * <pre>{
 *   "country": {...},
 *   "league": {...},
 *   "team": {...},
 *   "games": {
 *     "played": {"home": N, "away": N, "all": N},
 *     "wins":   {"home": {"total":N, "percentage":"0.5"}, "away":{...}, "all":{...}},
 *     "draws":  {"home": {...}, "away":{...}, "all":{...}},
 *     "loses":  {"home": {...}, "away":{...}, "all":{...}}
 *   },
 *   "points": {
 *     "for":     {"total": {"home":N, "away":N, "all":N},
 *                 "average": {"home":"X.X", "away":"X.X", "all":"X.X"}},
 *     "against": {... ayni sema ...}
 *   }
 * }</pre>
 *
 * <p>Bir yanit = bir takim icin tum sezon ozeti. Win/Lose percentage'leri
 * String gelir (API formatti). Caller {@code Number} veya {@code Map}'a
 * castlemek icin defansif parsing yapar.
 *
 * <p>Form bilgisi (son maclar "WWLWW") bu endpoint'te <b>yok</b> — caller
 * games tablosundan kendi hesaplar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkTeamStatisticsDto(
        BkTeamDto.Country country,
        Object league,
        BkTeamDto team,
        Games games,
        Points points
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Points(
            // 'for' Java keyword — Jackson @JsonProperty ile JSON anahtarini esle.
            @JsonProperty("for") ForAgainst forPoints,
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

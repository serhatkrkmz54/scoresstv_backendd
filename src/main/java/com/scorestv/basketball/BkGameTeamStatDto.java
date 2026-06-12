package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-Basketball {@code /games/statistics/teams?id=X} yanit ogesi.
 *
 * <p>Response array her zaman 2 satir (home + away). Toplu cagri (max 20 id)
 * desteklendiginde response 2N satir olur.
 *
 * <p>Yuzdeleri API "47.5" gibi string olarak doner (% isareti olmadan); biz
 * ham saklariz, UI gostermede "%" ekler.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkGameTeamStatDto(
        /** Sadece toplu cagrida dolu (id ile cagrida null). */
        Game game,
        TeamRef team,
        @JsonProperty("field_goals") MadeAttempt fieldGoals,
        @JsonProperty("threepoint_goals") MadeAttempt threepointGoals,
        @JsonProperty("freethrows_goals") MadeAttempt freethrowsGoals,
        Rebounds rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        @JsonProperty("personal_fouls") Integer personalFouls
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Game(Long id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    /** field_goals / threepoint_goals / freethrows_goals ortak yapi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MadeAttempt(Integer total, Integer attempts, String percentage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rebounds(Integer total, Integer offence, Integer defense) {}
}

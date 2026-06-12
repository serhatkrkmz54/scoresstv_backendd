package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-Basketball {@code /games/statistics/players?id=X} yanit ogesi.
 *
 * <p>Response game basina N satir (her takimdan starters + bench tum oyuncular).
 * {@code type} = "starters" veya "bench". {@code minutes} = "32:15" gibi
 * string. Stats yapilari takim DTO'su ile ayni (MadeAttempt + Rebounds).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkGamePlayerStatDto(
        Game game,
        TeamRef team,
        Player player,
        String type,
        String minutes,
        @JsonProperty("field_goals") BkGameTeamStatDto.MadeAttempt fieldGoals,
        @JsonProperty("threepoint_goals") BkGameTeamStatDto.MadeAttempt threepointGoals,
        @JsonProperty("freethrows_goals") BkGameTeamStatDto.MadeAttempt freethrowsGoals,
        BkGameTeamStatDto.Rebounds rebounds,
        Integer assists,
        Integer points,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        @JsonProperty("personal_fouls") Integer personalFouls
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Game(Long id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(Long id, String name) {}
}

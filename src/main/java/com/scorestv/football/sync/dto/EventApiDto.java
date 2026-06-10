package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /fixtures/events} yanıtındaki tek bir olay öğesi.
 *
 * <p>Tipler ve alt tipler (örnek): Goal/Normal Goal/Own Goal/Penalty/Missed
 * Penalty; Card/Yellow Card/Red Card; subst/Substitution 1; Var/Goal cancelled.
 * {@code time.extra} uzatma dakikasıdır (örn. 90+3 → 3); yoksa null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventApiDto(
        Time time,
        Team team,
        Player player,
        Player assist,
        String type,
        String detail,
        String comments
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Time(Integer elapsed, Integer extra) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(Long id, String name) {}
}

package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /players/squads?team=X} yanitinda bir element.
 *
 * <p>Yapi: {@code response[0] = { team: {...}, players: [{...}, ...] }}.
 * Tek tek oyuncular {@code players} dizisindedir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SquadApiDto(
        Team team,
        List<Player> players
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(
            Long id,
            String name,
            Integer age,
            Integer number,         // forma numarasi
            String position,        // Goalkeeper / Defender / Midfielder / Attacker
            String photo
    ) {}
}

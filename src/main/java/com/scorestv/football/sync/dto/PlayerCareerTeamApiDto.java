package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /players/teams?player=X} yaniti elementi.
 *
 * <p>Yanit her takim icin {@code seasons[]} dizisi doner (oyuncunun o takimda
 * oynadigi yillar). Ornek (Ö. Ali Sahin):
 * <pre>
 *   { team: {564, "Basaksehir"}, seasons: [2025, 2024, 2023, 2022, 2021, 2020] }
 *   { team: {607, "Konyaspor"}, seasons: [2019, 2018, 2017, 2016, ...] }
 *   { team: {777, "Türkiye"}, seasons: [2019] }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerCareerTeamApiDto(
        Team team,
        List<Integer> seasons
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}
}

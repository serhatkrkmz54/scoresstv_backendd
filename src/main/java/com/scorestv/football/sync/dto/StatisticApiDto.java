package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /fixtures/statistics} yanıtındaki tek bir takım bloğu.
 * Yanıt 2 öğe içerir: ev sahibi ve deplasman.
 *
 * <p>{@code statistics[].value} <b>karışık tip</b>: çoğunlukla Integer
 * ({@code 3}, {@code 9}, {@code 242}), bazen String ({@code "32%"} —
 * Ball Possession), bazen null (Passes %, Goalkeeper Saves). Bu yüzden DTO
 * tarafında {@code Object} tutarız; upserter String'e çevirip saklar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatisticApiDto(
        Team team,
        List<StatItem> statistics
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatItem(String type, Object value) {}
}

package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * API-Football {@code /standings} yanıtındaki tek bir lig öğesi.
 *
 * <p>{@code league.standings} <b>iki-iç-içe</b> array: dış array gruplar
 * (gruplu turnuvalarda CL grup A/B/...), iç array o gruptaki sıra. Normal
 * ligler için tek dış elemanlıdır.
 *
 * <p>{@code Goals.for} Java reserved word olduğu için record component
 * {@code goalsFor}'a {@code @JsonProperty("for")} ile eşlendi.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StandingApiDto(League league) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(
            Long id,
            String name,
            String country,
            String logo,
            String flag,
            Integer season,
            List<List<Row>> standings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            Integer rank,
            Team team,
            Integer points,
            Integer goalsDiff,
            String group,
            String form,
            /** "same" / "up" / "down" — sezon değişim oku için. */
            String status,
            String description,
            TeamStats all,
            TeamStats home,
            TeamStats away,
            String update
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamStats(
            Integer played,
            Integer win,
            Integer draw,
            Integer lose,
            Goals goals
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(
            @JsonProperty("for") Integer goalsFor,
            Integer against
    ) {}
}

package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-Basketball {@code /games} yanıt öğesi (tek maç).
 *
 * <p>Football'dan farkı: skorlar çeyrek bazlı ({@code quarter_1..4} + {@code
 * over_time} + {@code total}), olay timeline'ı yok. Bilinmeyen alanlar yok
 * sayılır (API zamanla alan ekleyebilir).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkGameDto(
        Long id,
        /** ISO-8601 offset'li tarih, örn. "2026-06-11T19:00:00+00:00". */
        String date,
        Long timestamp,
        String timezone,
        String stage,
        String week,
        Status status,
        League league,
        Country country,
        Teams teams,
        Scores scores
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
            @JsonProperty("long") String longName,
            @JsonProperty("short") String shortName,
            /** Oyun saati ("5:23") veya kalan dakika; string olabilir. */
            String timer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(Long id, String name, String type, String season, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(Long id, String name, String code, String flag) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Teams(TeamRef home, TeamRef away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(ScoreSide home, ScoreSide away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreSide(
            @JsonProperty("quarter_1") Integer q1,
            @JsonProperty("quarter_2") Integer q2,
            @JsonProperty("quarter_3") Integer q3,
            @JsonProperty("quarter_4") Integer q4,
            @JsonProperty("over_time") Integer overTime,
            Integer total) {}
}

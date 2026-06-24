package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-Volleyball {@code /games} yanit ogesi (tek mac).
 *
 * <p><b>Voleybol skor modeli (basketboldan FARKLI):</b>
 * <ul>
 *   <li>{@code scores.home/away} = KAZANILAN SET sayisi (0..3).</li>
 *   <li>{@code periods.first..fifth.home/away} = her setteki SAYI (orn 25-21),
 *       nullable. Voleybolda ceyrek/overtime YOK; timer/clock YOK (set bazli).</li>
 * </ul>
 *
 * <p>Bilinmeyen alanlar yok sayilir (API zamanla alan ekleyebilir).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbGameDto(
        Long id,
        /** ISO-8601 offset'li tarih, orn. "2026-06-11T19:00:00+00:00". */
        String date,
        String time,
        Long timestamp,
        String timezone,
        String week,
        Status status,
        League league,
        Country country,
        Teams teams,
        Scores scores,
        Periods periods
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
            @JsonProperty("long") String longName,
            @JsonProperty("short") String shortName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(Long id, String name, String type, String season, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(Long id, String name, String code, String flag) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Teams(TeamRef home, TeamRef away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    /** Kazanilan set sayilari (0..3). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scores(Integer home, Integer away) {}

    /** Set bazli sayilar — her set icin home/away (nullable). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Periods(
            SetScore first,
            SetScore second,
            SetScore third,
            SetScore fourth,
            SetScore fifth) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SetScore(Integer home, Integer away) {}
}

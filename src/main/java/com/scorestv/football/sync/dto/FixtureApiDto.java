package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API-Football {@code /fixtures} yanıtındaki tek bir maç öğesi.
 *
 * <p>Her öğe beş bloktan oluşur: {@code fixture} (maçın kendisi: tarih, durum,
 * stadyum), {@code league}, {@code teams}, {@code goals} (anlık skor) ve
 * {@code score} (yarı/normal/uzatma/penaltı kırılımı).
 *
 * <p>Buradaki league/team/venue bilgisi <b>kısmidir</b> (örn. venue yalnızca
 * id+ad+şehir). Tam detay ayrı {@code /venues}, {@code /teams}, {@code /leagues}
 * endpoint'lerinden gelir; bu DTO yalnızca fikstür senkronunun ihtiyacını taşır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FixtureApiDto(
        Fixture fixture,
        League league,
        Teams teams,
        Goals goals,
        Score score
) {

    /** Maçın kendisi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fixture(
            Long id,
            String referee,
            String timezone,
            /** ISO-8601, örn. "2026-05-25T16:00:00+00:00". */
            String date,
            Venue venue,
            Status status
    ) {}

    /** Stadyum (kısmi). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(Long id, String name, String city) {}

    /** Maç durumu. {@code long}/{@code short} Java anahtar kelimesi olduğu için yeniden adlandırıldı. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
            @JsonProperty("long") String longText,
            @JsonProperty("short") String shortCode,
            /** Canlı dakika; maç başlamadıysa null. */
            Integer elapsed,
            /** Uzatma dakikası (90+X); yoksa null. */
            Integer extra
    ) {}

    /** Lig (kısmi). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(
            Long id,
            String name,
            String country,
            String logo,
            String flag,
            Integer season,
            String round
    ) {}

    /** Ev sahibi ve deplasman takımları. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Teams(Team home, Team away) {}

    /** Takım (kısmi). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    /** Anlık skor. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(Integer home, Integer away) {}

    /** Skor kırılımı. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Score(
            GoalPair halftime,
            GoalPair fulltime,
            GoalPair extratime,
            GoalPair penalty
    ) {}

    /** Bir skor kırılımındaki ev/deplasman değeri. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalPair(Integer home, Integer away) {}
}

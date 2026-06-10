package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football top players endpoint'lerinin (topscorers / topassists /
 * topcards) ortak yanit yapisi.
 *
 * <p>Endpoint'ler:
 * <pre>
 *   GET /players/topscorers?league=X&season=Y
 *   GET /players/topassists?league=X&season=Y
 *   GET /players/topcards?league=X&season=Y
 * </pre>
 *
 * <p>Hepsi ayni response sekiline sahiptir: {@code response[i] = { player, statistics[] }}.
 * Hangi metrige bakacagimiz kategori karariyla degisir (goals.total /
 * goals.assists / cards.yellow). Tek DTO + kategori-disi parse logic
 * yeterli; ayri DTO yapmiyoruz.
 *
 * <p>{@code statistics} dizisi normalde tek elemanlidir (o ligin o sezonu);
 * bazen oyuncu birden fazla takimda oynamissa birden fazla satir olabilir
 * — biz ilk elemani aliriz (API zaten o ligin satirini ilk koyar).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TopPlayerApiDto(
        Player player,
        List<Statistics> statistics
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(
            Long id,
            String name,
            String firstname,
            String lastname,
            Integer age,
            String nationality,
            String photo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
            Team team,
            League league,
            Games games,
            Goals goals,
            Cards cards
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(Long id, String name, String country, Integer season) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Games(Integer appearences, Integer minutes) {}

    /** {@code total} = scorers icin, {@code assists} = assists icin metrik. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(Integer total, Integer conceded, Integer assists, Integer saves) {}

    /** {@code yellow} + {@code red} — cards kategorisi icin. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cards(Integer yellow, Integer yellowred, Integer red) {}
}

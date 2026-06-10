package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /teams} yanıtındaki tek bir öğe.
 *
 * <p>Her öğe takımın <b>tam</b> bilgisini ve <b>tam stadyum nesnesini</b>
 * (adres, kapasite, zemin, görsel) birlikte taşır — bu yüzden takım senkronu
 * hem takımı zenginleştirir hem stadyumu detaylandırır hem de takım↔stadyum
 * bağını kurar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamApiDto(
        Team team,
        Venue venue
) {

    /** Takımın tam bilgisi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(
            Long id,
            String name,
            String code,
            String country,
            Integer founded,
            Boolean national,
            String logo
    ) {}

    /** Takımın ev sahibi olduğu stadyum (tam detay). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(
            Long id,
            String name,
            String address,
            String city,
            Integer capacity,
            String surface,
            String image
    ) {}
}

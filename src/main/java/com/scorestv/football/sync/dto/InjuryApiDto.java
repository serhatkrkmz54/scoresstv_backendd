package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /injuries} yanıtındaki tek bir sakatlık/cezalı kaydı.
 *
 * <p><b>API quirk</b>: {@code type} ve {@code reason} TOP-LEVEL DEĞİL —
 * {@link Player} objesinin <b>içindedir</b>. Diğer endpoint'lerdeki desenden
 * farklıdır; upserter bunu mapper'da farklı işler.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InjuryApiDto(
        Player player,
        Team team,
        Fixture fixture,
        League league
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(
            Long id,
            String name,
            String photo,
            String type,
            String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fixture(Long id, String timezone, String date, Long timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(
            Long id, Integer season, String name,
            String country, String logo, String flag) {}
}

package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Basketball {@code /teams?league=X&season=Y} ve {@code /teams?id=X}
 * yanitlarinin ortak ogesi: takim bilgisi.
 *
 * <p>Ornek:
 * <pre>
 * {
 *   "id": 134, "name": "Fenerbahce", "logo": "https://...png",
 *   "national": false,
 *   "country": { "id": 39, "name": "Turkey", "code": "TR", "flag": "..." },
 *   "code": "FBA", "founded": 1913,
 *   "venue": { "name": "Ulker Sports Arena", "city": "Istanbul", "capacity": 13800 }
 * }
 * </pre>
 *
 * <p>Onceki cagrilarda sadece {@code id/name/logo} kullaniliyordu; kalan
 * alanlar nullable — backward-compat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkTeamDto(
        Long id,
        String name,
        String logo,
        Boolean national,
        Country country,
        String code,
        Integer founded,
        Venue venue
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(
            Long id,
            String name,
            String code,
            String flag
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Venue(
            String name,
            String city,
            Integer capacity
    ) {}
}

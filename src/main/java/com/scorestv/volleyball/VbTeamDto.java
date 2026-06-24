package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Volleyball {@code /teams?league=X&season=Y} ve {@code /teams?id=X}
 * yanitlarinin ortak ogesi: takim bilgisi.
 *
 * <p>API ornek:
 * <pre>{
 *   "id": 134, "name": "...", "logo": "...png", "national": false,
 *   "country": {"id":39,"name":"Turkey","code":"TR","flag":"..."}
 * }</pre>
 *
 * <p>Basketboldan farkli olarak voleybolda takim {@code code}/{@code founded}/
 * {@code venue} yok — sadece ulke + national bayragi var.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbTeamDto(
        Long id,
        String name,
        String logo,
        Boolean national,
        Country country
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(
            Long id,
            String name,
            String code,
            String flag
    ) {}
}

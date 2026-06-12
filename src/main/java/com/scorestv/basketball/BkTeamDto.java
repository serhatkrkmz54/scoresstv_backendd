package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Basketball {@code /teams?league=X&season=Y} yanıt öğesi: takım bilgisi.
 *
 * <p>Örnek:
 * <pre>
 * {
 *   "id": 134, "name": "Fenerbahce", "logo": "https://...png",
 *   "national": false, "country": { "id": 39, "name": "Turkey", "code": "TR", "flag": "..." }
 * }
 * </pre>
 *
 * <p>Şu an sadece {@code id, name, logo} ihtiyacımız var — kalanı ignore.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkTeamDto(
        Long id,
        String name,
        String logo
) {}

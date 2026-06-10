package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /sidelined?player=X} veya {@code ?players=A-B-C}
 * yaniti elementi.
 *
 * <p>Yapi flat: {type, start, end}. Bizim Upserter cagirana hangi player_id
 * verdiyse o oyuncuya atanir (API yaniti player_id'i icermiyor — sorgu
 * parametresinden bilinir).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SidelinedApiDto(
        String type,
        String start,
        String end
) {
}

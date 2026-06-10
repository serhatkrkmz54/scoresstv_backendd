package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /trophies?coach=X} veya {@code ?player=X} yaniti elementi.
 *
 * <p>Tek bir kupa kaydi: hangi turnuva, hangi ulke, hangi sezon, hangi
 * yer ("Winner" / "2nd Place" / ...). Yapi flat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrophyApiDto(
        String league,
        String country,
        String season,
        String place
) {
}

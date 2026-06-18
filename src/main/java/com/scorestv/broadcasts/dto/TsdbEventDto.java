package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TheSportsDB {@code searchevents.php} yanıtındaki tek event (yalnız
 * kullandığımız alanlar). {@code idAPIfootball} API-Football fixture id'sidir —
 * eşleştirmenin anahtarı.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbEventDto(
        String idEvent,
        String idAPIfootball,
        String strHomeTeam,
        String strAwayTeam,
        String dateEvent,
        String strSport
) {}

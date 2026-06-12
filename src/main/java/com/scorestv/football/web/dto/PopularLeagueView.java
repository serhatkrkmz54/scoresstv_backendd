package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Sol ray "Popüler Ligler" listesi öğesi. ID config'te (popular-league-ids)
 * belirlenir; ad/slug dile göre lokalize gelir.
 */
public record PopularLeagueView(
        Long id,
        String name,
        String slug,
        String logo,
        String country,
        String countryFlag
) implements Serializable {
}

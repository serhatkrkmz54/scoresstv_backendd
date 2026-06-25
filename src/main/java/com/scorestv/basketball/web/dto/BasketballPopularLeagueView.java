package com.scorestv.basketball.web.dto;

import java.io.Serializable;

/**
 * Sol ray "Popüler Ligler" listesi öğesi (basketbol). ID config'te
 * (popular-league-ids) belirlenir; ad/slug dile göre lokalize gelir.
 * Futbol {@code PopularLeagueView} esi — alanlar sade tutuldu.
 */
public record BasketballPopularLeagueView(
        Long id,
        String name,
        String slug,
        String logo
) implements Serializable {
}

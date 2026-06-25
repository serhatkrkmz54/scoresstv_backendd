package com.scorestv.basketball.web.dto;

import java.io.Serializable;

/**
 * Sol ray "Popüler Takımlar" listesi öğesi (basketbol). ID config'te
 * (popular-team-ids) belirlenir; ad/slug dile göre lokalize gelir.
 * Futbol {@code PopularTeamView} esi — alanlar sade tutuldu.
 */
public record BasketballPopularTeamView(
        Long id,
        String name,
        String slug,
        String logo
) implements Serializable {
}

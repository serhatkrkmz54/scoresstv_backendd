package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Bir takımın o maçtaki tüm oyuncu istatistikleri.
 * MatchDetailResponse.playerStats: [home, away] sıralı.
 *
 * @param teamId  Hangi takımın oyuncuları
 * @param players O takım için tüm oyuncu satırları (ilk 11 + oynayan yedekler)
 */
public record PlayerStatGroup(
        Long teamId,
        List<PlayerStatView> players
) implements Serializable {
}

package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BasketballPlayerSeasonStatRepository
        extends JpaRepository<BasketballPlayerSeasonStat, Long> {

    Optional<BasketballPlayerSeasonStat> findByPlayerIdAndLeagueIdAndSeason(
            Long playerId, Long leagueId, String season);

    /**
     * Bir lig + sezon icin tum oyuncu sezonluk istatistikleri.
     * Top players sync'i bu listeyi cekip PPG/RPG/APG'ye gore siralar.
     */
    @Query("SELECT s FROM BasketballPlayerSeasonStat s " +
            "WHERE s.league.id = :leagueId AND s.season = :season")
    List<BasketballPlayerSeasonStat> findByLeagueAndSeason(
            @Param("leagueId") Long leagueId,
            @Param("season") String season);

    /**
     * Bir oyuncunun tum sezon istatistikleri (oyuncu profil sayfasi icin).
     */
    @Query("SELECT s FROM BasketballPlayerSeasonStat s " +
            "WHERE s.player.id = :playerId " +
            "ORDER BY s.season DESC")
    List<BasketballPlayerSeasonStat> findByPlayerOrderBySeasonDesc(
            @Param("playerId") Long playerId);

    /**
     * D-Faz2: Bir takimin lig + sezon kadrosu (roster).
     * Player + Team JOIN FETCH; oyuncu adi alfabetik sirali.
     */
    @Query("SELECT s FROM BasketballPlayerSeasonStat s " +
            "JOIN FETCH s.player " +
            "LEFT JOIN FETCH s.team " +
            "WHERE s.team.id = :teamId " +
            "  AND s.league.id = :leagueId " +
            "  AND s.season = :season " +
            "ORDER BY s.player.name")
    List<BasketballPlayerSeasonStat> findRosterByTeamLeagueSeason(
            @Param("teamId") Long teamId,
            @Param("leagueId") Long leagueId,
            @Param("season") String season);
}

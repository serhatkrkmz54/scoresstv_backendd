package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Takim+lig+sezon istatistik kayitlarina erisim. */
public interface TeamStatisticsRepository extends JpaRepository<TeamStatistics, Long> {

    /** Bir takimin belirli lig+sezon istatistigi — sync upsert anahtari. */
    Optional<TeamStatistics> findByTeamIdAndLeagueIdAndSeason(
            Long teamId, Long leagueId, Integer season);

    /**
     * Bir takimin belirli sezondaki TUM lig istatistiklerini join'siz doner.
     * Lig referansi LAZY proxy — caller liglerden gelen id'lere gore baska
     * sorgu yapabilir. Tek takimin sayfasini yuklemek icin kullanilir.
     */
    @Query("SELECT s FROM TeamStatistics s "
            + "JOIN FETCH s.league "
            + "WHERE s.team.id = :teamId AND s.season = :season")
    List<TeamStatistics> findByTeamIdAndSeasonWithLeague(
            @Param("teamId") Long teamId, @Param("season") Integer season);
}

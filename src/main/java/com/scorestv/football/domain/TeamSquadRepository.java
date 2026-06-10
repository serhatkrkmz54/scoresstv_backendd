package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Takim kadrosuna erisim. */
public interface TeamSquadRepository extends JpaRepository<TeamSquad, Long> {

    /** Bir takimin belirli sezon kadrosu — pozisyon sirali olmadan, caller siralar. */
    @Query("SELECT s FROM TeamSquad s "
            + "WHERE s.team.id = :teamId AND s.season = :season")
    List<TeamSquad> findByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);

    /**
     * Replace pattern: bir takim+sezonun TUM kadrosunu siler. Sonraki upsert
     * tam set yazar. UNIQUE constraint yarismasini engellemek icin
     * {@code @Modifying @Query} JPQL DELETE (immediate flush).
     */
    @Modifying
    @Query("DELETE FROM TeamSquad s WHERE s.team.id = :teamId AND s.season = :season")
    void deleteByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);
}

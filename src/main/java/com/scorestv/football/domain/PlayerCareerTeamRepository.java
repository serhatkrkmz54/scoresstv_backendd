package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Oyuncu kariyer takimlarina erisim. */
public interface PlayerCareerTeamRepository extends JpaRepository<PlayerCareerTeam, Long> {

    /**
     * Bir oyuncunun tum kariyer takimlari. Team JOIN FETCH (UI logo + slug
     * gosterir). Siralama icin sezon en yenisi onde olmali; ham veride sonsuz
     * dizi var, caller cozer.
     */
    @Query("SELECT t FROM PlayerCareerTeam t "
            + "JOIN FETCH t.team "
            + "WHERE t.playerId = :playerId")
    List<PlayerCareerTeam> findByPlayerIdWithTeam(@Param("playerId") Long playerId);

    /** Replace: bir oyuncunun kariyer takimlarini sifirla, gelen tam set yazilsin. */
    @Modifying
    @Query("DELETE FROM PlayerCareerTeam t WHERE t.playerId = :playerId")
    void deleteByPlayerId(@Param("playerId") Long playerId);

    /** Empty-check: lazy sync icin. */
    @Query("SELECT COUNT(t) FROM PlayerCareerTeam t WHERE t.playerId = :playerId")
    long countByPlayerId(@Param("playerId") Long playerId);
}

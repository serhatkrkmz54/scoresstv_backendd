package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Oyuncu kupa kayitlarina erisim. */
public interface PlayerTrophyRepository extends JpaRepository<PlayerTrophy, Long> {

    /** Bir oyuncunun tum kupalari, sezona gore yeni → eski. */
    @Query("SELECT t FROM PlayerTrophy t "
            + "WHERE t.playerId = :playerId "
            + "ORDER BY t.season DESC NULLS LAST, t.place ASC")
    List<PlayerTrophy> findByPlayerIdOrderBySeason(@Param("playerId") Long playerId);

    /** Replace: bir oyuncunun kupalarini sifirla, gelen tam set yazilsin. */
    @Modifying
    @Query("DELETE FROM PlayerTrophy t WHERE t.playerId = :playerId")
    void deleteByPlayerId(@Param("playerId") Long playerId);

    /** Empty-check: lazy sync icin. */
    @Query("SELECT COUNT(t) FROM PlayerTrophy t WHERE t.playerId = :playerId")
    long countByPlayerId(@Param("playerId") Long playerId);
}

package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Mac basina oyuncu istatistikleri — game basina N satir (kadro). */
public interface BasketballGamePlayerStatRepository
        extends JpaRepository<BasketballGamePlayerStat, Long> {

    /**
     * Tek mac icin tum oyuncu satirlari (her iki takim, starters + bench).
     * Team + Player JOIN FETCH (UI'da grup + sirala icin meta gerekli).
     */
    @Query("""
            select s from BasketballGamePlayerStat s
            join fetch s.team
            join fetch s.player
            where s.game.id = :gameId
            order by s.team.id asc, s.type asc, s.points desc nulls last
            """)
    List<BasketballGamePlayerStat> findByGameId(@Param("gameId") Long gameId);

    /** Replace sync icin. */
    @Modifying
    @Query("delete from BasketballGamePlayerStat s where s.game.id = :gameId")
    void deleteByGameId(@Param("gameId") Long gameId);

    /** Empty-check (lazy sync tetikleyicisi). */
    long countByGameId(Long gameId);
}

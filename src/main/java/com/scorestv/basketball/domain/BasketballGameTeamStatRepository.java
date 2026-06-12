package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Mac basina takim istatistikleri — game basina 2 satir. */
public interface BasketballGameTeamStatRepository
        extends JpaRepository<BasketballGameTeamStat, Long> {

    /**
     * Tek mac icin tum (home + away) takim istatistikleri.
     * Team JOIN FETCH (logo + isim hazir).
     */
    @Query("""
            select s from BasketballGameTeamStat s
            join fetch s.team
            where s.game.id = :gameId
            order by s.id asc
            """)
    List<BasketballGameTeamStat> findByGameId(@Param("gameId") Long gameId);

    /** Replace sync icin — eski satirlari sil. */
    @Modifying
    @Query("delete from BasketballGameTeamStat s where s.game.id = :gameId")
    void deleteByGameId(@Param("gameId") Long gameId);

    /** Empty-check (lazy sync tetikleyicisi). */
    long countByGameId(Long gameId);
}

package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Basketbol maçlarına erişim. Serving sorguları league+team JOIN FETCH ile. */
public interface BasketballGameRepository extends JpaRepository<BasketballGame, Long> {

    /** Bir gün aralığındaki maçlar (anasayfa fikstür). */
    @Query("""
            select g from BasketballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.startAt >= :start and g.startAt < :end
            order by g.startAt asc
            """)
    List<BasketballGame> findDayWithDetails(@Param("start") Instant start,
                                            @Param("end") Instant end);

    /** Belirli durumdaki maçlar (canlı = Q1/Q2/HT/Q3/Q4/OT/BT...). */
    @Query("""
            select g from BasketballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.statusShort in :statuses
            order by g.startAt asc
            """)
    List<BasketballGame> findByStatusWithDetails(@Param("statuses") Collection<String> statuses);

    /** Tek maç (detay). */
    @Query("""
            select g from BasketballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id = :id
            """)
    Optional<BasketballGame> findOneWithDetails(@Param("id") Long id);

    /** Id listesindeki maçlar (favoriler) — league+team JOIN FETCH. */
    @Query("""
            select g from BasketballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id in :ids
            order by g.startAt asc
            """)
    List<BasketballGame> findByIdsWithDetails(@Param("ids") Collection<Long> ids);

    /**
     * B-Faz6: Bir ligin oynanmis/oynanacak maclarindaki distinct sezonlar —
     * yeni → eski. Standings cold-start oldugunda sezon discover icin.
     */
    @Query("""
            select distinct g.season from BasketballGame g
            where g.league.id = :leagueId and g.season is not null
            order by g.season desc
            """)
    List<String> findDistinctSeasonsByLeagueId(@Param("leagueId") Long leagueId);

    /**
     * B-Faz4: H2H — iki takim arasi gecmis maclar, mevcut mac haric,
     * yeniden eskiye sirali. Pageable ile ust limit (orn. 10).
     */
    @Query("""
            select g from BasketballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id <> :currentId
              and ((g.homeTeam.id = :t1 and g.awayTeam.id = :t2)
                or (g.homeTeam.id = :t2 and g.awayTeam.id = :t1))
            order by g.startAt desc
            """)
    List<BasketballGame> findH2h(@Param("t1") Long t1,
                                  @Param("t2") Long t2,
                                  @Param("currentId") Long currentId,
                                  Pageable pageable);
}

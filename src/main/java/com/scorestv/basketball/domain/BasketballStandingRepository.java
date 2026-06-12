package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Basketbol puan durumu. Sorgular team JOIN FETCH ile (logo + isim hazır). */
public interface BasketballStandingRepository extends JpaRepository<BasketballStanding, Long> {

    /**
     * Bir lig + sezon icin tum standings — grup adina ve pozisyona gore.
     * NBA gibi liglerde grup ayrimi vardir; tek-grup liglerde {@code groupName} = "".
     */
    @Query("""
            select s from BasketballStanding s
            join fetch s.team
            where s.league.id = :leagueId and s.season = :season
            order by s.groupName asc, s.position asc nulls last, s.team.name asc
            """)
    List<BasketballStanding> findByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                                   @Param("season") String season);

    /** Belirli bir takimin satiri (takim sayfasi standings widget'i icin). */
    @Query("""
            select s from BasketballStanding s
            join fetch s.team
            where s.league.id = :leagueId and s.season = :season and s.team.id = :teamId
            """)
    List<BasketballStanding> findForTeam(@Param("leagueId") Long leagueId,
                                        @Param("season") String season,
                                        @Param("teamId") Long teamId);

    /** Sayim — empty-check icin (lazy sync tetikleyicisi). */
    long countByLeagueIdAndSeason(Long leagueId, String season);

    /** Toplu replace icin — sync'ten once temizlik. */
    @Query("""
            delete from BasketballStanding s
            where s.league.id = :leagueId and s.season = :season
            """)
    @org.springframework.data.jpa.repository.Modifying
    void deleteByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                 @Param("season") String season);

    /**
     * B-Faz6: Bu ligin standings tablosundaki distinct sezonlar — yeni → eski.
     * Sezon dropdown ve default sezon secimi icin.
     */
    @Query("""
            select distinct s.season from BasketballStanding s
            where s.league.id = :leagueId
            order by s.season desc
            """)
    List<String> findDistinctSeasonsByLeagueId(@Param("leagueId") Long leagueId);
}

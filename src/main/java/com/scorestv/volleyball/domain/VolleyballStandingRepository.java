package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Voleybol puan durumu. Sorgular team JOIN FETCH ile (logo + isim hazir). */
public interface VolleyballStandingRepository extends JpaRepository<VolleyballStanding, Long> {

    /** Bir lig + sezon icin tum standings — grup adina ve pozisyona gore. */
    @Query("""
            select s from VolleyballStanding s
            join fetch s.team
            where s.league.id = :leagueId and s.season = :season
            order by s.groupName asc, s.position asc nulls last, s.team.name asc
            """)
    List<VolleyballStanding> findByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                                   @Param("season") String season);

    /** Belirli bir takimin satiri (takim sayfasi standings widget'i icin). */
    @Query("""
            select s from VolleyballStanding s
            join fetch s.team
            where s.league.id = :leagueId and s.season = :season and s.team.id = :teamId
            """)
    List<VolleyballStanding> findForTeam(@Param("leagueId") Long leagueId,
                                        @Param("season") String season,
                                        @Param("teamId") Long teamId);

    /** Sayim — empty-check icin (lazy sync tetikleyicisi). */
    long countByLeagueIdAndSeason(Long leagueId, String season);

    /** Toplu replace icin — sync'ten once temizlik. */
    @Modifying
    @Query("""
            delete from VolleyballStanding s
            where s.league.id = :leagueId and s.season = :season
            """)
    void deleteByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                 @Param("season") String season);

    /** Bu ligin standings tablosundaki distinct sezonlar — yeni → eski. */
    @Query("""
            select distinct s.season from VolleyballStanding s
            where s.league.id = :leagueId
            order by s.season desc
            """)
    List<String> findDistinctSeasonsByLeagueId(@Param("leagueId") Long leagueId);
}

package com.scorestv.volleyball.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Voleybol maclarina erisim. Serving sorgulari league+team JOIN FETCH ile. */
public interface VolleyballGameRepository extends JpaRepository<VolleyballGame, Long> {

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.startAt >= :start and g.startAt < :end
            order by g.startAt asc
            """)
    List<VolleyballGame> findDayWithDetails(@Param("start") Instant start,
                                            @Param("end") Instant end);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.statusShort in :statuses
            order by g.startAt asc
            """)
    List<VolleyballGame> findByStatusWithDetails(@Param("statuses") Collection<String> statuses);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id = :id
            """)
    Optional<VolleyballGame> findOneWithDetails(@Param("id") Long id);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id in :ids
            order by g.startAt asc
            """)
    List<VolleyballGame> findByIdsWithDetails(@Param("ids") Collection<Long> ids);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.statusShort in ('FT', 'AW')
              and g.startAt >= :from
              and g.startAt < :until
            order by g.startAt asc
            """)
    List<VolleyballGame> findRecentlyFinished(@Param("from") Instant from,
                                              @Param("until") Instant until);

    @Query("""
            select distinct g.season from VolleyballGame g
            where g.league.id = :leagueId and g.season is not null
            order by g.season desc
            """)
    List<String> findDistinctSeasonsByLeagueId(@Param("leagueId") Long leagueId);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where g.id <> :currentId
              and ((g.homeTeam.id = :t1 and g.awayTeam.id = :t2)
                or (g.homeTeam.id = :t2 and g.awayTeam.id = :t1))
            order by g.startAt desc
            """)
    List<VolleyballGame> findH2h(@Param("t1") Long t1,
                                 @Param("t2") Long t2,
                                 @Param("currentId") Long currentId,
                                 Pageable pageable);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where (g.homeTeam.id = :teamId or g.awayTeam.id = :teamId)
              and (:leagueId is null or g.league.id = :leagueId)
              and (:season is null or g.season = :season)
              and g.startAt < CURRENT_TIMESTAMP
            order by g.startAt desc
            """)
    List<VolleyballGame> findRecentByTeam(@Param("teamId") Long teamId,
                                          @Param("leagueId") Long leagueId,
                                          @Param("season") String season,
                                          Pageable pageable);

    @Query("""
            select g from VolleyballGame g
            join fetch g.league
            join fetch g.homeTeam
            join fetch g.awayTeam
            where (g.homeTeam.id = :teamId or g.awayTeam.id = :teamId)
              and (:leagueId is null or g.league.id = :leagueId)
              and (:season is null or g.season = :season)
              and g.startAt >= CURRENT_TIMESTAMP
            order by g.startAt asc
            """)
    List<VolleyballGame> findUpcomingByTeam(@Param("teamId") Long teamId,
                                            @Param("leagueId") Long leagueId,
                                            @Param("season") String season,
                                            Pageable pageable);

    @Query("""
            select g.league.id, g.season from VolleyballGame g
            where (g.homeTeam.id = :teamId or g.awayTeam.id = :teamId)
              and g.league.id is not null
              and g.season is not null
            group by g.league.id, g.season
            order by max(g.startAt) desc
            """)
    List<Object[]> findTeamLeagueSeasonPairs(@Param("teamId") Long teamId,
                                             Pageable pageable);
}

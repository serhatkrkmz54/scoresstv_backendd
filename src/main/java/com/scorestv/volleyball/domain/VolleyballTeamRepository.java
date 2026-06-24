package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VolleyballTeamRepository extends JpaRepository<VolleyballTeam, Long> {

    /** Henuz aynalanmamis (logoKey bos) ama API logosu olan takimlar — image mirror. */
    List<VolleyballTeam> findTop200ByLogoKeyIsNullAndLogoIsNotNull();

    /** Slug ile arama — public team endpoint slug-based URL kullanir. */
    Optional<VolleyballTeam> findBySlug(String slug);

    /** Daily refresh job covered takimlari gunluk tazeler. */
    List<VolleyballTeam> findByCoveredTrue();

    /**
     * Bir voleybol liginde (opsiyonel sezon filtresi) oynamis DISTINCT takimlar.
     * Iliski {@code volleyball_games}'ten turetilir (home_team veya away_team).
     */
    @Query("""
        SELECT DISTINCT t FROM VolleyballTeam t
        WHERE t.id IN (
            SELECT g.homeTeam.id FROM VolleyballGame g
            WHERE g.league.id = :leagueId
              AND (:season IS NULL OR g.season = :season)
        )
        OR t.id IN (
            SELECT g.awayTeam.id FROM VolleyballGame g
            WHERE g.league.id = :leagueId
              AND (:season IS NULL OR g.season = :season)
        )
        ORDER BY t.name
        """)
    List<VolleyballTeam> findTeamsInLeague(@Param("leagueId") Long leagueId,
                                           @Param("season") String season);
}

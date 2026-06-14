package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BasketballTeamRepository extends JpaRepository<BasketballTeam, Long> {

    /** Henüz aynalanmamış (logoKey boş) ama API logosu olan takımlar — image mirror. */
    List<BasketballTeam> findTop200ByLogoKeyIsNullAndLogoIsNotNull();

    /** Slug ile arama — public team endpoint slug-based URL kullanir. */
    Optional<BasketballTeam> findBySlug(String slug);

    /** DailyBasketballTeamRefreshJob covered takimlari gunluk tazeler. */
    List<BasketballTeam> findByCoveredTrue();

    /**
     * Bir basketbol liginde (opsiyonel sezon filtresi) oynamış DISTINCT takımlar.
     * Basketbolda team-league junction tablosu YOK; ilişki
     * {@code basketball_games}'ten türetilir (home_team veya away_team).
     *
     * <p>Sezon verilmezse o ligin tüm geçmiş takımları döner; sezon verilirse
     * sadece o sezon kadrosu (önerilen — onboarding "current season").
     */
    @Query("""
        SELECT DISTINCT t FROM BasketballTeam t
        WHERE t.id IN (
            SELECT g.homeTeam.id FROM BasketballGame g
            WHERE g.league.id = :leagueId
              AND (:season IS NULL OR g.season = :season)
        )
        OR t.id IN (
            SELECT g.awayTeam.id FROM BasketballGame g
            WHERE g.league.id = :leagueId
              AND (:season IS NULL OR g.season = :season)
        )
        ORDER BY t.name
        """)
    List<BasketballTeam> findTeamsInLeague(@Param("leagueId") Long leagueId,
                                           @Param("season") String season);
}

package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Lig+sezon+kategori bazli top-N oyuncu erisimi. */
public interface LeagueTopPlayerRepository
        extends JpaRepository<LeagueTopPlayer, Long> {

    /**
     * Bir lig+sezon+kategorinin tum top oyuncularini rank sirali doner
     * (API zaten sirali doner; biz de o sirayla saklariz).
     */
    @Query("SELECT p FROM LeagueTopPlayer p "
            + "WHERE p.league.id = :leagueId "
            + "  AND p.season = :season "
            + "  AND p.category = :category "
            + "ORDER BY p.rank ASC")
    List<LeagueTopPlayer> findByLeagueSeasonCategory(
            @Param("leagueId") Long leagueId,
            @Param("season") Integer season,
            @Param("category") LeagueTopPlayer.Category category);

    /**
     * Replace pattern: bir lig+sezon+kategorinin tum satirlarini siler.
     * Sonraki upsert tam set'i yazar. @Modifying @Query JPQL DELETE — derived
     * deleteBy yerine immediate SQL gonder (UNIQUE-constraint yaris onlemi,
     * digerlerinde uyguladigimiz desen).
     */
    @Modifying
    @Query("DELETE FROM LeagueTopPlayer p "
            + "WHERE p.league.id = :leagueId "
            + "  AND p.season = :season "
            + "  AND p.category = :category")
    void deleteByLeagueSeasonCategory(
            @Param("leagueId") Long leagueId,
            @Param("season") Integer season,
            @Param("category") LeagueTopPlayer.Category category);
}

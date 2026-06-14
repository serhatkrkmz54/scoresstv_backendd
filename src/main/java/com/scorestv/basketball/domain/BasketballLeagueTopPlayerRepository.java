package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BasketballLeagueTopPlayerRepository
        extends JpaRepository<BasketballLeagueTopPlayer, Long> {

    /**
     * Bir lig + sezon + kategori icin top 10 sirali liste.
     * Detay sayfasi okuma yolu.
     */
    @Query("SELECT t FROM BasketballLeagueTopPlayer t " +
            "WHERE t.league.id = :leagueId " +
            "  AND t.season = :season " +
            "  AND t.category = :category " +
            "ORDER BY t.position ASC")
    List<BasketballLeagueTopPlayer> findByLeagueSeasonCategory(
            @Param("leagueId") Long leagueId,
            @Param("season") String season,
            @Param("category") BasketballLeagueTopPlayer.Category category);

    /**
     * Bir lig + sezon icin TUM kategoriler — detay sayfasi tek queryle
     * 3 kategoriyi cekip groupBy yapsin.
     */
    @Query("SELECT t FROM BasketballLeagueTopPlayer t " +
            "WHERE t.league.id = :leagueId AND t.season = :season " +
            "ORDER BY t.category ASC, t.position ASC")
    List<BasketballLeagueTopPlayer> findAllByLeagueAndSeason(
            @Param("leagueId") Long leagueId,
            @Param("season") String season);

    /**
     * Replace stratejisi: (league, season, category) icin once sil sonra
     * insert. Sync'in atomik refresh'inde kullanilir.
     */
    @Modifying
    @Query("DELETE FROM BasketballLeagueTopPlayer t " +
            "WHERE t.league.id = :leagueId " +
            "  AND t.season = :season " +
            "  AND t.category = :category")
    int deleteByLeagueSeasonCategory(
            @Param("leagueId") Long leagueId,
            @Param("season") String season,
            @Param("category") BasketballLeagueTopPlayer.Category category);
}

package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link BasketballTeamSeasonStat} icin lookup repository.
 *
 * <p>Bir takim + lig + sezon kombinasyonu icin tek satir doner; bulamazsa
 * Upserter yeni satir olusturur ({@code save}).
 */
public interface BasketballTeamSeasonStatRepository
        extends JpaRepository<BasketballTeamSeasonStat, Long> {

    /** Takim + lig + sezon icin (Upserter ve detay servisi kullanir). */
    Optional<BasketballTeamSeasonStat> findByTeamIdAndLeagueIdAndSeason(
            Long teamId, Long leagueId, String season);
}

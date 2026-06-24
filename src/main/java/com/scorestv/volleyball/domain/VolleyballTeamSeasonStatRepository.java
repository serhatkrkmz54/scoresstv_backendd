package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link VolleyballTeamSeasonStat} icin lookup repository.
 *
 * <p>Bir takim + lig + sezon kombinasyonu icin tek satir doner; bulamazsa
 * Upserter yeni satir olusturur ({@code save}).
 */
public interface VolleyballTeamSeasonStatRepository
        extends JpaRepository<VolleyballTeamSeasonStat, Long> {

    /** Takim + lig + sezon icin (Upserter ve detay servisi kullanir). */
    Optional<VolleyballTeamSeasonStat> findByTeamIdAndLeagueIdAndSeason(
            Long teamId, Long leagueId, String season);
}

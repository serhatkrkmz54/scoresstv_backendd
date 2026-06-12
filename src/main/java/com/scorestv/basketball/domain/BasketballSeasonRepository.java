package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Basketbol lig+sezon coverage cache — standings DailyJob icin. */
public interface BasketballSeasonRepository extends JpaRepository<BasketballSeason, Long> {

    Optional<BasketballSeason> findByLeagueIdAndSeason(Long leagueId, String season);

    /** Standings cekilecek (covered=true) tum lig+sezon satirlari. */
    List<BasketballSeason> findByCoverageStandingsTrue();
}

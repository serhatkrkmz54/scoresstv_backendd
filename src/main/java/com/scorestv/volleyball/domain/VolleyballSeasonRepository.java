package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Voleybol lig+sezon coverage cache — standings DailyJob icin. */
public interface VolleyballSeasonRepository extends JpaRepository<VolleyballSeason, Long> {

    Optional<VolleyballSeason> findByLeagueIdAndSeason(Long leagueId, String season);

    /** Standings cekilecek (covered=true) tum lig+sezon satirlari. */
    List<VolleyballSeason> findByCoverageStandingsTrue();
}

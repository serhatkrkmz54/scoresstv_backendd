package com.scorestv.broadcasts.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lig varsayilan yayinci atamasi DB erisimi. */
public interface LeagueBroadcasterRepository extends JpaRepository<LeagueBroadcaster, Long> {

    /** Verilen lig + sezon + ulke icin tum kanallar, sirayla. */
    List<LeagueBroadcaster>
        findByLeagueIdAndSeasonAndCountryCodeOrderBySortOrderAsc(
            Long leagueId, Integer season, String countryCode);

    /** Belirli ligin tum sezonlardaki yayinci listesi — admin paneli. */
    List<LeagueBroadcaster> findByLeagueIdOrderBySeasonDescSortOrderAsc(Long leagueId);
}

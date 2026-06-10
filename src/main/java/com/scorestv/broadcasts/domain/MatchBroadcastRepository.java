package com.scorestv.broadcasts.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Mac bazli yayin override DB erisimi. */
public interface MatchBroadcastRepository extends JpaRepository<MatchBroadcast, Long> {

    /** Verilen mac + ulke icin tum override kanallar, sirayla. */
    List<MatchBroadcast> findByFixtureIdAndCountryCodeOrderBySortOrderAsc(
            Long fixtureId, String countryCode);

    /** Maca ait tum override'lar (tum ulkeler) — admin paneli. */
    List<MatchBroadcast> findByFixtureIdOrderBySortOrderAsc(Long fixtureId);
}

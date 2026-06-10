package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Stadyum verisine erişim. */
public interface VenueRepository extends JpaRepository<Venue, Long> {

    /** Türkçe adı girilmiş (name_tr dolu) stadyum sayısı — çeviri durumu içindir. */
    long countByNameTrIsNotNull();

    /** Gorseli henuz aynalanmamis stadyumlar (max 200) — image mirror icin. */
    List<Venue> findTop200ByImageKeyIsNullAndImageUrlIsNotNull();
}

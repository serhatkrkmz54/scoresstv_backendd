package com.scorestv.broadcasts.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** TV kanali master DB erisimi. */
public interface TvChannelRepository extends JpaRepository<TvChannel, Long> {

    /** Belirli ulkenin aktif kanallari, sirayla. */
    List<TvChannel> findByCountryCodeAndActiveTrueOrderBySortOrderAsc(String countryCode);

    /** Tum aktif kanallar — admin listesi. */
    List<TvChannel> findByActiveTrueOrderByCountryCodeAscSortOrderAsc();
}

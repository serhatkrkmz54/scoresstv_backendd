package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VolleyballLeagueRepository extends JpaRepository<VolleyballLeague, Long> {

    /** Aynalanmamis lig logolari (logoKey bos, logo var). */
    List<VolleyballLeague> findTop200ByLogoKeyIsNullAndLogoIsNotNull();

    /** Aynalanmamis ulke bayraklari (countryFlagKey bos, countryFlag var). */
    List<VolleyballLeague> findTop200ByCountryFlagKeyIsNullAndCountryFlagIsNotNull();

    /** Slug -> lig (detay sayfasi URL'den cozumlemesi). */
    Optional<VolleyballLeague> findBySlug(String slug);

    /** Covered ligler — daily refresh job. */
    List<VolleyballLeague> findByCoveredTrue();
}

package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BasketballLeagueRepository extends JpaRepository<BasketballLeague, Long> {

    /** Aynalanmamış lig logoları (logoKey boş, logo var). */
    List<BasketballLeague> findTop200ByLogoKeyIsNullAndLogoIsNotNull();

    /** Aynalanmamış ülke bayrakları (countryFlagKey boş, countryFlag var). */
    List<BasketballLeague> findTop200ByCountryFlagKeyIsNullAndCountryFlagIsNotNull();
}

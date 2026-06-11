package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BasketballTeamRepository extends JpaRepository<BasketballTeam, Long> {

    /** Henüz aynalanmamış (logoKey boş) ama API logosu olan takımlar — image mirror. */
    List<BasketballTeam> findTop200ByLogoKeyIsNullAndLogoIsNotNull();
}

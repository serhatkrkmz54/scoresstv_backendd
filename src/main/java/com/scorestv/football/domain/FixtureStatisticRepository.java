package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Maç istatistiklerine erişim. */
public interface FixtureStatisticRepository
        extends JpaRepository<FixtureStatistic, Long> {

    /**
     * Bir maçın TÜM istatistiklerini (her iki takım için), team LAZY proxy.
     * Maç detayı bunu okuyup home/away karşılaştırmalı yapıya dönüştürür.
     */
    @Query("SELECT s FROM FixtureStatistic s "
            + "WHERE s.fixture.id = :fixtureId")
    List<FixtureStatistic> findByFixtureId(@Param("fixtureId") Long fixtureId);

    /**
     * Replace pattern — bir maçın tüm istatistiklerini siler.
     *
     * <p>{@code @Modifying @Query} ile JPQL DELETE; derived {@code deleteByXxx}
     * load-then-remove yapardı, IDENTITY-PK insert'lerle UNIQUE(fixture,team,type)
     * çakışırdı. Bu pattern SQL DELETE'i hemen gönderir → INSERT'ten önce gider.
     */
    @Modifying
    @Query("DELETE FROM FixtureStatistic s WHERE s.fixture.id = :fixtureId")
    void deleteByFixtureId(@Param("fixtureId") Long fixtureId);
}

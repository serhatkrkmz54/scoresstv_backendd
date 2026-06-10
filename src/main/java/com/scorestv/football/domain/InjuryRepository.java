package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Sakatlık / cezalı listesine erişim. */
public interface InjuryRepository extends JpaRepository<Injury, Long> {

    /** Bir maçın tüm sakatlık/cezalı kayıtları; team LAZY proxy. */
    @Query("SELECT i FROM Injury i WHERE i.fixture.id = :fixtureId")
    List<Injury> findByFixtureId(@Param("fixtureId") Long fixtureId);

    /** Replace pattern — bir maçın tüm injury kayıtlarını siler. */
    void deleteByFixtureId(Long fixtureId);
}

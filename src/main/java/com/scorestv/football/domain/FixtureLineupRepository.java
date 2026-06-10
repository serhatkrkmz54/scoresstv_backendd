package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Maç kadrolarına (takım bazlı) erişim. */
public interface FixtureLineupRepository extends JpaRepository<FixtureLineup, Long> {

    /** Bir maçın iki kadrosu (ev + deplasman). Takım LAZY proxy; id okumak güvenli. */
    List<FixtureLineup> findByFixtureId(Long fixtureId);

    /** Bir maç + takım için kadro — upsert anahtar araması. */
    Optional<FixtureLineup> findByFixtureIdAndTeamId(Long fixtureId, Long teamId);

    /**
     * Bir maçın kadrolarını team JOIN FETCH ile getirir — maç detayı için
     * (N+1 önler).
     */
    @Query("SELECT l FROM FixtureLineup l "
            + "JOIN FETCH l.team "
            + "WHERE l.fixture.id = :fixtureId")
    List<FixtureLineup> findByFixtureIdWithTeam(@Param("fixtureId") Long fixtureId);

    /** Belirli bir takımda kadro var mı? */
    boolean existsByFixtureId(Long fixtureId);

    /**
     * Bir takimin son oynanan macindaki bench'te oturan kocun id'si — bu
     * API'nin "su an aktif bas antrenor kim?" sorusuna en guvenilir cevabi.
     * {@code /coachs?team=X} ayni anda birden cok coach end=null dondurebilir
     * (eski antrenor kovuldu ama API guncellenmedi); lineup ise her macta
     * gercekten bench'te oturan kisidir.
     *
     * <p>Sirali: en son kickoff'lu mac (yalniz lineup'i synclenmis ve coachId
     * dolu olanlar). Birden cok mac ayni kickoff'a sahipse herhangi biri.
     */
    @Query("SELECT l.coachId FROM FixtureLineup l "
            + "WHERE l.team.id = :teamId AND l.coachId IS NOT NULL "
            + "ORDER BY l.fixture.kickoffAt DESC")
    List<Long> findMostRecentCoachIds(
            @Param("teamId") Long teamId,
            org.springframework.data.domain.Pageable pageable);
}

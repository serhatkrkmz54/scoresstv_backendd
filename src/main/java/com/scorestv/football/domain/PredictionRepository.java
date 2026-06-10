package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Maç tahminlerine erişim. */
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    /** Bir maçın tahmini — UPSERT için kullanılır. */
    Optional<Prediction> findByFixtureId(Long fixtureId);
}

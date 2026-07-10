package com.scorestv.bilyoner;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FixtureOddsSnapshotRepository
        extends JpaRepository<FixtureOddsSnapshot, Long> {

    /** Bu fixture için verilen kaynak+tür snapshot'ı zaten var mı? (idempotency) */
    boolean existsByFixtureIdAndSourceAndSnapshotKind(
            Long fixtureId, String source, String snapshotKind);
}

package com.scorestv.reporter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Muhabir ↔ lig atamaları. ÜST SEVİYE arayüz — Spring Data nested taramaz. */
public interface ReporterAssignmentRepository extends JpaRepository<ReporterAssignment, Long> {

    List<ReporterAssignment> findByUserIdAndActiveTrue(Long userId);

    Optional<ReporterAssignment> findByUserIdAndLeagueIdAndActiveTrue(Long userId, Long leagueId);

    List<ReporterAssignment> findAllByOrderByCreatedAtDesc();
}

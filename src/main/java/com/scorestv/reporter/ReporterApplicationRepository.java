package com.scorestv.reporter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Muhabir başvuruları. ÜST SEVİYE arayüz — Spring Data nested taramaz. */
public interface ReporterApplicationRepository extends JpaRepository<ReporterApplication, Long> {

    Page<ReporterApplication> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ReporterApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<ReporterApplication> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndStatus(Long userId, String status);

    long countByStatus(String status);
}

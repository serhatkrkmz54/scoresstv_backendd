package com.scorestv.football.insight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiPredictionRepository extends JpaRepository<AiPrediction, Long> {

    /** Henüz notlanmamış tahminler (maç bittiyse notlanacak). */
    List<AiPrediction> findByGradedFalse();

    /**
     * Notlanmış tahminlerin özeti (verilen tarihten bugüne). resultTotal =
     * favori dolu (başabaş olmayan) maç sayısı; diğer toplamlar = total.
     */
    @Query("SELECT new com.scorestv.football.insight.AiAgg("
            + "COUNT(p), "
            + "SUM(CASE WHEN p.pickResult IS NOT NULL THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.resultHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.ouHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.bttsHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.exactHit = true THEN 1L ELSE 0L END)) "
            + "FROM AiPrediction p WHERE p.graded = true AND p.kickoffAt >= :start")
    AiAgg aggregate(@Param("start") Instant start);

    /**
     * Ay bazında özet (yıl, ay, total, resultTotal, resultHits, ouHits,
     * bttsHits, exactHits) — son 12 ay kırılımı için. Object[] map'lenir.
     */
    @Query("SELECT EXTRACT(YEAR FROM p.kickoffAt), EXTRACT(MONTH FROM p.kickoffAt), "
            + "COUNT(p), "
            + "SUM(CASE WHEN p.pickResult IS NOT NULL THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.resultHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.ouHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.bttsHit = true THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN p.exactHit = true THEN 1L ELSE 0L END) "
            + "FROM AiPrediction p WHERE p.graded = true AND p.kickoffAt >= :start "
            + "GROUP BY EXTRACT(YEAR FROM p.kickoffAt), EXTRACT(MONTH FROM p.kickoffAt) "
            + "ORDER BY EXTRACT(YEAR FROM p.kickoffAt) DESC, "
            + "         EXTRACT(MONTH FROM p.kickoffAt) DESC")
    List<Object[]> monthlyAggregate(@Param("start") Instant start);
}

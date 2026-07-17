package com.scorestv.football.insight;

/**
 * JPQL toplama (constructor expression) sonucu — notlanmış tahminlerin özeti.
 * Alanlar null olabilir (hiç satır yoksa SUM null döner).
 */
public record AiAgg(
        Long total,
        Long resultTotal, // favori (pick_result) dolu olan maç sayısı
        Long resultHits,
        Long ouHits,
        Long bttsHits,
        Long exactHits) {
}

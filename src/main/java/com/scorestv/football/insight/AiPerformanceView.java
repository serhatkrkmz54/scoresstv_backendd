package com.scorestv.football.insight;

import java.util.List;

/**
 * AI Analiz isabet karnesi — istemciye dönen özet. Üç zaman dilimi (son 30 gün,
 * son 365 gün, tüm zaman) + son 12 ayın kırılımı. Yüzdeler 0..100 tamsayı.
 */
public record AiPerformanceView(
        AiStatBlock month,   // son 30 gün
        AiStatBlock quarter, // son 90 gün (3 ay)
        AiStatBlock year,    // son 365 gün
        AiStatBlock all,     // tüm zaman
        List<AiMonthBlock> months) {

    /** Bir zaman dilimi için kalem kalem isabet. */
    public record AiStatBlock(
            long total,        // notlanmış maç
            long resultTotal,  // favori verilen (başabaş olmayan) maç
            long resultHits, int resultPct,   // maç sonucu (1X2)
            long ouHits, int ouPct,           // alt/üst 2.5
            long bttsHits, int bttsPct,       // karşılıklı gol
            long exactHits, int exactPct,     // tam skor
            int overallPct) {                 // birleşik isabet (1X2+AÜ+KG)
    }

    /** Ay bazında kısa özet (grafik/tablo için). */
    public record AiMonthBlock(
            String ym, // "2026-07"
            long total,
            int overallPct,
            int resultPct,
            int ouPct,
            int bttsPct,
            int exactPct) {
    }
}

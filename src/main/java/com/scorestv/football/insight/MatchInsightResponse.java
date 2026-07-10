package com.scorestv.football.insight;

import java.util.List;

/**
 * "AI Analiz" yanıtı — modelin bir maç için kalibre olasılıkları.
 *
 * <p>Yüzdeler 0..100 tamsayı. {@code available=false} ise yeterli veri yok
 * (yeni sezon / az maç) → istemci "yeterli veri yok" gösterir.
 *
 * <p>{@code topScores}: en olası ilk 3 kesin skor (her biri yüzdesiyle) — tek
 * skor yerine, çünkü futbolda tek en-olası skor genelde 1-1 çıkar ve yanıltıcıdır.
 *
 * <p><b>Bu bir istatistik analizdir, bahis tavsiyesi değildir.</b>
 */
public record MatchInsightResponse(
        boolean available,
        Integer homeWinPct,
        Integer drawPct,
        Integer awayWinPct,
        Integer over25Pct,
        Integer under25Pct,
        Integer bttsYesPct,
        Integer bttsNoPct,
        Double expectedGoalsHome,
        Double expectedGoalsAway,
        List<ScoreLine> topScores,
        String favorite,      // "HOME" | "DRAW" | "AWAY" | null
        String confidence,    // yerelleştirilmiş: Yüksek/Orta/Düşük
        String summary,       // sayıları kelimeye döken analiz okuması (tüyo DEĞİL)
        String note) {

    /** Tek bir olası skor ve yüzdesi (örn. "2-1", 11). */
    public record ScoreLine(String score, int pct) {}

    /** Yeterli veri yokken dönülen boş yanıt. */
    public static MatchInsightResponse unavailable(String note) {
        return new MatchInsightResponse(
                false, null, null, null, null, null, null, null,
                null, null, null, null, null, null, note);
    }
}

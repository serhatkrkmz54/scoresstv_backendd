package com.scorestv.football.insight;

/**
 * "AI Analiz" yanıtı — modelin bir maç için kalibre olasılıkları.
 *
 * <p>Yüzdeler 0..100 tamsayı. {@code available=false} ise yeterli veri yok
 * (yeni sezon / az maç) → istemci "yeterli veri yok" gösterir.
 *
 * <p>{@code expectedScore}: beklenen gol (λ) yuvarlanarak elde edilen YAKLAŞIK
 * skor — tanım gereği gol beklentisi ve favoriyle tutarlı (tek "en olası kesin
 * skor" hep 1-1 çıktığı ve toplam-gol ile çeliştiği için o kaldırıldı).
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
        String expectedScore,   // yaklaşık beklenen skor, örn. "2-1"
        String favorite,        // "HOME" | "DRAW" | "AWAY" | null (başa baş)
        String confidence,      // yerelleştirilmiş: Yüksek/Orta/Düşük
        String summary,         // sayıları kelimeye döken analiz okuması (tüyo DEĞİL)
        String note,
        // ---- Biten maç: "Sonuç Karnesi" için ----
        boolean finished,       // maç bitti mi (FT/AET/PEN...) → istemci tahminleri sonuçla kıyaslar
        Integer actualHome,     // gerçek skor (yalnız finished iken dolu)
        Integer actualAway) {

    /** Yeterli veri yokken dönülen boş yanıt. */
    public static MatchInsightResponse unavailable(String note) {
        return new MatchInsightResponse(
                false, null, null, null, null, null, null, null,
                null, null, null, null, null, null, note,
                false, null, null);
    }
}

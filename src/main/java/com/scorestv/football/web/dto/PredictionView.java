package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Maç tahmini — kazanan, yüzde, bahis önerisi, takım karşılaştırma + zengin
 * teams performans verisi.
 *
 * <p>Tüm yüzde değerleri String ("60%", "1.2", ...) — API böyle döner;
 * frontend parse eder.
 *
 * @param teams API'nin {@code teams.home}/{@code teams.away} altindaki tum
 *              zengin performans verisi (last_5, league form/fixtures/goals,
 *              biggest, clean_sheet, lineups, cards, vb.). JSONB passthrough.
 *              Yapı: {@code { "home": {...}, "away": {...} }}. Frontend
 *              dogrudan kullanir.
 */
public record PredictionView(
        Winner winner,
        Boolean winOrDraw,
        String advice,
        String underOver,
        Goals goals,
        Percent percent,
        Comparison comparison,
        Map<String, Object> teams
) implements Serializable {

    /**
     * @param comment     API ham yorumu ("Win or draw", "Win", ...)
     * @param commentText Dile çevrilmiş yorum ("Galibiyet veya Beraberlik", ...)
     */
    public record Winner(Long teamId, String comment, String commentText) implements Serializable {}

    public record Goals(String home, String away) implements Serializable {}

    /** Maç sonucu olasılıkları (ev galibiyeti / beraberlik / deplasman galibiyeti). */
    public record Percent(String home, String draw, String away) implements Serializable {}

    /**
     * Takım karşılaştırması — radar chart / progress bar için.
     * Her alanın home/away yüzdeleri toplamı normalde %100.
     */
    public record Comparison(
            Pair form,
            Pair att,
            Pair def,
            Pair poisson,
            Pair h2h,
            Pair goals,
            Pair total
    ) implements Serializable {}

    public record Pair(String home, String away) implements Serializable {}
}

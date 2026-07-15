package com.scorestv.game;

/**
 * Düellonun kapıştığı istatistik. Çözümleme servisi bunu FixturePlayerStat
 * alanlarına eşler ve dönemdeki maçlarda TOPLAR.
 */
public enum DuelMetric {
    RATING,          // ortalama maç reytingi
    GOALS,           // toplam gol
    ASSISTS,         // toplam asist
    KEY_PASSES,      // kilit pas
    ASSISTS_KEYPASS, // asist + kilit pas
    SHOTS_ON,        // isabetli şut
    SAVES,           // kurtarış (kaleci)
    CLEAN_SHEET,     // gol yemeden bitirilen maç sayısı (kaleci/defans)
    DUELS_WON,       // kazanılan ikili mücadele
    TACKLES_INT,     // top kapma + kesme (interception)
    DRIBBLES,        // başarılı çalım
    CARDS,           // toplam kart (sarı+kırmızı) — LOWER
    FOULS            // yapılan faul — LOWER
}

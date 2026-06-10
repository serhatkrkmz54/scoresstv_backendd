package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Belirli bir günün maçları, lige göre gruplu — anasayfa fikstür yanıtı.
 *
 * @param date         sorgulanan tarih (ISO, yyyy-MM-dd)
 * @param fixtureCount o günkü toplam maç sayısı
 * @param liveCount    o gün şu an canlı oynanan maç sayısı (banner için)
 * @param leagues      lig lig gruplanmış maçlar (kapsanan ligler üstte)
 */
public record FixtureDayResponse(
        String date,
        int fixtureCount,
        int liveCount,
        List<LeagueGroup> leagues
) implements Serializable {
}

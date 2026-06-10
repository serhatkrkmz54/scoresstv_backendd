package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Anasayfa tarih şeridi — ±N gün için (toplam, canlı) maç sayıları.
 *
 * <p>Frontend bunu üst kısımdaki tarih şeridini doldurmak için kullanır:
 * her gün için kaç maç var, kaçı şu an canlı, hangisi "Bugün". Aktif tarih
 * seçilince {@code GET /api/v1/fixtures?date=...} ile o günün maçları çekilir.
 *
 * @param today site saat dilimine göre bugünün tarihi (ISO, yyyy-MM-dd)
 * @param dates pencere içindeki günler — en eski önce, en yeni sonra
 */
public record FixtureDatesResponse(
        String today,
        List<DateEntry> dates
) implements Serializable {

    /**
     * Tek bir günün özeti.
     *
     * @param date         ISO tarih (yyyy-MM-dd)
     * @param dayName      gösterim adı: "Bugün" / "Yarın" / "Dün" ya da dile
     *                     göre kısa gün adı ("Pzt", "Sal"...)
     * @param fixtureCount o gün için kayıtlı toplam maç sayısı
     * @param liveCount    o gün şu an canlı oynanan maç sayısı
     */
    public record DateEntry(
            String date,
            String dayName,
            long fixtureCount,
            long liveCount
    ) implements Serializable {
    }
}

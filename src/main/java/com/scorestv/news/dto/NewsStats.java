package com.scorestv.news.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Panel dashboard (ana sayfa ozet) istatistikleri. Tek bir admin ucundan
 * ({@code GET /api/v1/admin/news/stats}) doner; ek istek gerektirmez.
 *
 * <ul>
 *   <li>Ust kartlar: toplam / durum sayilari / toplam goruntulenme / donemsel yayin.</li>
 *   <li>{@code trend}: son 14 gun, gun basina yayinlanan haber sayisi (yerel gun).</li>
 *   <li>{@code topViewed}: en cok okunan yayinda haberler.</li>
 *   <li>{@code editors}: yazar basina toplam + yayinda haber sayisi.</li>
 *   <li>{@code recentActivity}: son denetim (audit) olaylari.</li>
 * </ul>
 */
public record NewsStats(
        long total,
        long published,
        long draft,
        long scheduled,
        long archived,
        long totalViews,
        long publishedToday,
        long publishedThisWeek,
        long publishedThisMonth,
        long breaking,
        long featured,
        List<TrendPoint> trend,
        List<TopArticle> topViewed,
        List<EditorStat> editors,
        List<ActivityItem> recentActivity
) implements Serializable {

    /** Trend noktasi — gun (yyyy-MM-dd, yerel) + o gun yayinlanan haber sayisi. */
    public record TrendPoint(String date, long count) implements Serializable {
    }

    /** En cok okunan haber ozeti (panelde link + gorsel icin yeterli alan). */
    public record TopArticle(
            long id,
            String title,
            String slug,
            String lang,
            String status,
            long viewCount,
            String publishedAt
    ) implements Serializable {
    }

    /** Yazar (editor) uretim ozeti. */
    public record EditorStat(
            long authorId,
            String name,
            long total,
            long published
    ) implements Serializable {
    }

    /** Son aktivite ogesi — kim, hangi eylem, hangi habere, ne zaman. */
    public record ActivityItem(
            String action,
            Long articleId,
            String articleTitle,
            Long actorId,
            String actorName,
            String at
    ) implements Serializable {
    }
}

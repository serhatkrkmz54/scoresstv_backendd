package com.scorestv.news.dto;

import com.scorestv.news.NewsCategory;

import java.io.Serializable;
import java.time.Instant;

/**
 * Haber liste ogesi (public liste + admin liste kartlari). Govde (body)
 * icermez — hafif.
 */
public record NewsListItem(
        Long id,
        String slug,
        String lang,
        String title,
        String summary,
        String coverImageUrl,
        NewsCategory category,
        String sport,
        boolean isBreaking,
        boolean isFeatured,
        Instant publishedAt,
        Integer readingMinutes
) implements Serializable {
}

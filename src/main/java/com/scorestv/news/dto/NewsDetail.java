package com.scorestv.news.dto;

import com.scorestv.news.NewsCategory;
import com.scorestv.news.NewsStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Haber detay yaniti — sanitize edilmis govde (body) + bagli varlik hafif
 * referanslari + ceviri esi dilleri.
 */
public record NewsDetail(
        Long id,
        String slug,
        String lang,
        String title,
        String summary,
        String body,
        String coverImageUrl,
        NewsStatus status,
        NewsCategory category,
        String sport,
        boolean isBreaking,
        boolean isFeatured,
        String authorName,
        long viewCount,
        Integer readingMinutes,
        String source,
        String sourceUrl,
        Instant publishedAt,
        Long translationGroupId,
        List<String> availableLangs,
        List<EntityRef> teams,
        List<EntityRef> leagues,
        List<EntityRef> countries,
        List<EntityRef> players,
        List<FixtureRef> fixtures
) implements Serializable {

    /** Bagli varlik hafif referansi. logo/photo/flag URL'si (varsa). */
    public record EntityRef(
            Long id,
            String name,
            String logo
    ) implements Serializable {
    }

    /**
     * Bagli mac (fixture) hafif referansi. {@code id} fixture id'sidir;
     * {@code name} "Ev - Deplasman" formatinda, {@code logo} ev takimi
     * logosu (varsa), {@code kickoff} mac baslangic zamani.
     */
    public record FixtureRef(
            Long id,
            String name,
            String logo,
            java.time.Instant kickoff
    ) implements Serializable {
    }
}

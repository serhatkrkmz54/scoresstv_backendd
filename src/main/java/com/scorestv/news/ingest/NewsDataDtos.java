package com.scorestv.news.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * NewsData.io {@code /latest} yanıt DTO'ları. Yalnız kullandığımız alanlar;
 * geri kalan (keywords, creator, content[ücretli], video_url, ...) yok sayılır.
 */
public final class NewsDataDtos {

    private NewsDataDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            String status,
            @JsonProperty("totalResults") Integer totalResults,
            List<Article> results,
            @JsonProperty("nextPage") String nextPage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Article(
            @JsonProperty("article_id") String articleId,
            String title,
            String link,
            String description,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("source_id") String sourceId,
            @JsonProperty("source_name") String sourceName,
            @JsonProperty("pubDate") String pubDate
    ) {}
}

package com.scorestv.news.dto;

import com.scorestv.news.NewsCategory;
import com.scorestv.news.NewsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Yeni haber olusturma istegi. body sunucuda sanitize edilir; slug basliktan
 * uretilir (istemci gondermez). status verilmezse DRAFT.
 */
public record CreateNewsRequest(
        @NotBlank(message = "Dil bos olamaz.")
        @Pattern(regexp = "tr|en", message = "Dil 'tr' veya 'en' olmalidir.")
        String lang,

        Long translationGroupId,

        @NotBlank(message = "Baslik bos olamaz.")
        @Size(max = 255, message = "Baslik 255 karakteri asamaz.")
        String title,

        @Size(max = 600, message = "Ozet 600 karakteri asamaz.")
        String summary,

        @NotBlank(message = "Icerik bos olamaz.")
        String body,

        @Size(max = 255)
        String coverImageKey,

        NewsCategory category,

        @Size(max = 16)
        String sport,

        boolean isBreaking,
        boolean isFeatured,

        NewsStatus status,

        Instant publishedAt,

        @Size(max = 64)
        String source,

        @Size(max = 1024)
        String sourceUrl,

        List<Long> teamIds,
        List<Long> leagueIds,
        List<Long> countryIds,
        List<Long> playerIds
) {
}

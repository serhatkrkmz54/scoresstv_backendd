package com.scorestv.news.dto;

import com.scorestv.news.BulkAction;
import com.scorestv.news.NewsCategory;

import java.util.List;

/**
 * Panel toplu haber islemi istegi. {@code ids} bos olamaz. {@code category}
 * yalniz {@code SET_CATEGORY}, {@code sport} yalniz {@code SET_SPORT} icin
 * kullanilir; digerlerinde yok sayilir.
 */
public record BulkNewsRequest(
        List<Long> ids,
        BulkAction action,
        NewsCategory category,
        String sport
) {
}

package com.scorestv.news.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Sayfali haber listesi yaniti — liste + toplam + sonraki sayfa var mi.
 */
public record NewsPageResponse(
        List<NewsListItem> items,
        long totalCount,
        boolean hasNext
) implements Serializable {
}

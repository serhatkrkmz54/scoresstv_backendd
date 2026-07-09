package com.scorestv.news.dto;

import java.io.Serializable;
import java.util.List;

/** Denetim gunlugu sayfa yaniti (news liste deseniyle ayni sekil). */
public record NewsAuditPage(
        List<NewsAuditView> items,
        long totalCount,
        boolean hasNext
) implements Serializable {
}

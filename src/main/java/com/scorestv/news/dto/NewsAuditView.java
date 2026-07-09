package com.scorestv.news.dto;

import java.io.Serializable;

/**
 * Denetim gunlugu (audit log) satiri — kim (actor) hangi habere hangi eylemi
 * ne zaman uyguladi. actorName/articleTitle backend'de cozülerek doldurulur.
 */
public record NewsAuditView(
        Long id,
        String action,
        Long articleId,
        String articleTitle,
        Long actorId,
        String actorName,
        String at,
        String meta
) implements Serializable {
}

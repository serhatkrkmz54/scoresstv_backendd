package com.scorestv.news.dto;

import com.scorestv.news.NewsStatus;

import java.time.Instant;

/**
 * Yayin takviminden HIZLI yeniden zamanlama. {@code publishedAt} yeni yayin
 * zamani; {@code status} opsiyonel (verilirse SCHEDULED/PUBLISHED gecisi
 * uygulanir, yoksa mevcut durum korunur). Tam duzenleme gerektirmez.
 */
public record RescheduleRequest(
        Instant publishedAt,
        NewsStatus status
) {
}

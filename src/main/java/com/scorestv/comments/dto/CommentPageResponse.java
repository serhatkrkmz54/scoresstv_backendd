package com.scorestv.comments.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Sayfali yorum cevabi. {@code totalCount}: macın toplam aktif yorum sayisi
 * (tab badge'i icin). {@code hasNext}: daha sayfa var mi (mobile pagination).
 */
public record CommentPageResponse(
        List<CommentView> items,
        long totalCount,
        boolean hasNext
) implements Serializable {}

package com.scorestv.volleyball.web.dto;

/**
 * Sync sonucu basit metrik (mobile log/debug).
 *
 * @param savedCount     yazilan abonelik (FK guard sonrasi gecerli)
 * @param requestedCount istekteki id sayisi
 */
public record SyncFavoriteVolleyballResponse(
        int savedCount,
        int requestedCount
) {}

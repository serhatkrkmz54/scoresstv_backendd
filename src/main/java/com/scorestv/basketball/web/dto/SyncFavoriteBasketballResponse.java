package com.scorestv.basketball.web.dto;

/**
 * Sync sonucu basit metrik (mobile log/debug).
 *
 * @param savedCount     yazılan abonelik (FK guard sonrası geçerli)
 * @param requestedCount istekteki id sayısı
 */
public record SyncFavoriteBasketballResponse(
        int savedCount,
        int requestedCount
) {}

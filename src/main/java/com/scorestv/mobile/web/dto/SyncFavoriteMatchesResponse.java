package com.scorestv.mobile.web.dto;

/**
 * Sync sonucunda backend'in dondurdugu basit metrik —
 * mobile log/debug icin (kullanici tarafinda kullanilmaz).
 *
 * @param savedCount yazilan abonelik sayisi (FK guard sonrasi gecerli olanlar)
 * @param requestedCount istekteki id sayisi
 */
public record SyncFavoriteMatchesResponse(
        int savedCount,
        int requestedCount
) {}

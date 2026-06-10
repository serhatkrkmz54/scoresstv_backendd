package com.scorestv.mobile.web.dto;

/** Sync sonrasi sayilar — debug ve client gosterimi icin. */
public record SyncNotificationPrefsResponse(
        Long deviceTokenId,
        int upserted,
        int removed
) {}

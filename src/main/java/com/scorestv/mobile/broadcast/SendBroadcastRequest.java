package com.scorestv.mobile.broadcast;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin panelinden genel bildirim gonderme istegi.
 * platform/lang null gelirse ALL kabul edilir.
 */
public record SendBroadcastRequest(
        @NotBlank(message = "Baslik bos olamaz")
        @Size(max = 200, message = "Baslik en fazla 200 karakter")
        String title,

        @NotBlank(message = "Metin bos olamaz")
        @Size(max = 1000, message = "Metin en fazla 1000 karakter")
        String body,

        @Size(max = 1000, message = "Baglanti en fazla 1000 karakter")
        String link,

        BroadcastPlatform platform,

        BroadcastLang lang
) {
}

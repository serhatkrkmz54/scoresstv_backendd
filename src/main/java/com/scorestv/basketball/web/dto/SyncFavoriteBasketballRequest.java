package com.scorestv.basketball.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mobile favori basketbol maçı listesini backend'e batch sync etme isteği
 * (replace pattern, idempotent). Maks 200 favori.
 */
public record SyncFavoriteBasketballRequest(
        @NotBlank
        String fcmToken,

        @NotNull
        @Size(max = 200, message = "En fazla 200 favori basketbol maçı kaydedilebilir")
        List<Long> gameIds
) {}

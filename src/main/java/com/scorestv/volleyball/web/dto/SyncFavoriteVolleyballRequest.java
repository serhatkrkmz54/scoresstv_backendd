package com.scorestv.volleyball.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Mobile favori voleybol maci listesini backend'e batch sync etme istegi
 * (replace pattern, idempotent). Maks 200 favori.
 */
public record SyncFavoriteVolleyballRequest(
        @NotBlank
        String fcmToken,

        @NotNull
        @Size(max = 200, message = "En fazla 200 favori voleybol maci kaydedilebilir")
        List<Long> gameIds
) {}

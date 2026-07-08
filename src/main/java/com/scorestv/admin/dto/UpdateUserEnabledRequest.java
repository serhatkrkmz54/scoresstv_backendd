package com.scorestv.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Kullanici etkin/pasif degisikligi (ADMIN). */
public record UpdateUserEnabledRequest(
        @NotNull(message = "Durum zorunlu")
        Boolean enabled
) {}

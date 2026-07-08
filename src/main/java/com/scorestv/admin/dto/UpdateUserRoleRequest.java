package com.scorestv.admin.dto;

import com.scorestv.user.Role;
import jakarta.validation.constraints.NotNull;

/** Kullanici rol degisikligi (ADMIN). */
public record UpdateUserRoleRequest(
        @NotNull(message = "Rol zorunlu")
        Role role
) {}

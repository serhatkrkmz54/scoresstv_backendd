package com.scorestv.admin.dto;

import com.scorestv.user.Role;
import com.scorestv.user.User;

/**
 * Editör yönetimi listesi için güvenli kullanıcı görünümü.
 * Sifre HASH'i ASLA dahil edilmez.
 */
public record AdminUserView(
        Long id,
        String email,
        String displayName,
        Role role,
        boolean enabled
) {
    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isEnabled()
        );
    }
}

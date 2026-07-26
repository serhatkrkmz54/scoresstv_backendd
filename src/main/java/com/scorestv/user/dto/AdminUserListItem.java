package com.scorestv.user.dto;

import com.scorestv.user.Role;
import com.scorestv.user.User;

import java.time.Instant;

/**
 * Panel "Üyeler" listesi satırı — {@link UserResponse}'tan farklı olarak
 * yönetim alanlarını (enabled, provider, createdAt) taşır. Şifre hash'i
 * asla dönmez.
 */
public record AdminUserListItem(
        Long id,
        String email,
        String displayName,
        Role role,
        boolean enabled,
        String country,
        /** Giriş sağlayıcısı: google | apple | local (öncelik sırasıyla). */
        String provider,
        Instant createdAt
) {
    public static AdminUserListItem from(User u) {
        final String provider = u.getGoogleId() != null ? "google"
                : u.getAppleId() != null ? "apple"
                : "local";
        return new AdminUserListItem(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isEnabled(),
                u.getCountry(),
                provider,
                u.getCreatedAt());
    }
}

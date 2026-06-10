package com.scorestv.user.dto;

/**
 * ADMIN'in bir kullanicinin tum oturumlarini sonlandirmasinin sonucu.
 */
public record LogoutAllResponse(
        Long userId,
        int revokedSessions
) {}

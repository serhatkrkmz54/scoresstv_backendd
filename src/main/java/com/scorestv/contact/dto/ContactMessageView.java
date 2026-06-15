package com.scorestv.contact.dto;

import com.scorestv.contact.ContactMessage;

import java.time.Instant;

/**
 * Admin listesi için iletişim mesajı görünümü.
 */
public record ContactMessageView(
        Long id,
        String name,
        String email,
        String subject,
        String message,
        String status,
        String ipAddress,
        Instant createdAt
) {
    public static ContactMessageView of(ContactMessage m) {
        return new ContactMessageView(
                m.getId(),
                m.getName(),
                m.getEmail(),
                m.getSubject(),
                m.getMessage(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getIpAddress(),
                m.getCreatedAt());
    }
}

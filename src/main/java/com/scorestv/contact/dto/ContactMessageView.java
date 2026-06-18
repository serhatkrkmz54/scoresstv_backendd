package com.scorestv.contact.dto;

import com.scorestv.contact.ContactMessage;

import java.time.Instant;
import java.util.List;

/**
 * Admin listesi için iletişim mesajı görünümü. Mobil "Bize Ulaşın"
 * bildirimlerinde {@code source="mobile"} ve resim/video {@code attachments}
 * gelir; admin panelinde ekler URL ile görüntülenir.
 */
public record ContactMessageView(
        Long id,
        String name,
        String email,
        String subject,
        String message,
        String status,
        String source,
        String ipAddress,
        Instant createdAt,
        List<AttachmentView> attachments
) {
    /** Tek bir ek (resim/video) görünümü. */
    public record AttachmentView(
            String url,
            String contentType,
            String originalName,
            Long fileSize
    ) {}

    public static ContactMessageView of(ContactMessage m) {
        List<AttachmentView> atts = (m.getAttachments() == null)
                ? List.of()
                : m.getAttachments().stream()
                        .map(a -> new AttachmentView(
                                a.getUrl(), a.getContentType(),
                                a.getOriginalName(), a.getFileSize()))
                        .toList();
        return new ContactMessageView(
                m.getId(),
                m.getName(),
                m.getEmail(),
                m.getSubject(),
                m.getMessage(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getSource(),
                m.getIpAddress(),
                m.getCreatedAt(),
                atts);
    }
}

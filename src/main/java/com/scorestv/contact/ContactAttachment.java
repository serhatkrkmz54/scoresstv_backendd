package com.scorestv.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bir iletişim/sorun-bildirimi mesajına bağlı resim veya video eki.
 *
 * <p>Dosyanın kendisi MinIO'ya yüklenir; burada yalnız {@code storage_key}
 * (silme için), herkese açık {@code url} (admin görüntüleme için) ve meta
 * (içerik tipi, boyut, orijinal ad) tutulur.
 */
@Entity
@Table(name = "contact_attachment")
@Getter
@Setter
@NoArgsConstructor
public class ContactAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private ContactMessage message;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(nullable = false, length = 600)
    private String url;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}

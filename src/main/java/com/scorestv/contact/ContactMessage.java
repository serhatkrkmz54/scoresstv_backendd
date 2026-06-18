package com.scorestv.contact;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * İletişim formundan gelen mesaj. Anonim (giriş gerektirmez) olarak yazılır;
 * admin panelinden listelenir ve durumu güncellenir.
 */
@Entity
@Table(name = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 160)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactStatus status = ContactStatus.NEW;

    /** Mesajın geldiği kanal: "web" | "mobile". */
    @Column(nullable = false, length = 16)
    private String source = "web";

    /** Gönderenin IP'si (X-Forwarded-For veya remoteAddr). */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** Resim/video ekleri (mobil "Bize Ulaşın"). */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ContactAttachment> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** İki yönlü ilişkiyi tutarlı kurar. */
    public void addAttachment(ContactAttachment a) {
        a.setMessage(this);
        this.attachments.add(a);
    }
}

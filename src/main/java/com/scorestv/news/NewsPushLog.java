package com.scorestv.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Haber push OUTBOX satiri (ayni zamanda idempotency kaydi).
 *
 * <p>{@code article_id} UNIQUE — bir haber en fazla BIR kez push edilir.
 * Yayinlama transaction'i satiri {@code PENDING} olarak ATOMIK ekler
 * (ON CONFLICT DO NOTHING); {@link NewsNotificationService} atomik claim +
 * lease ile gonderir. Durumlar: PENDING (sirada) → SENDING (lease'li) →
 * SENT | FAILED. Hata → backoff'la {@code next_attempt_at} ileri atilir.
 */
@Entity
@Table(name = "news_push_log")
@Getter
@Setter
@NoArgsConstructor
public class NewsPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, unique = true)
    private Long articleId;

    /** ALL | FAVORITES (gonderim aninda secilen hedef). */
    @Column(nullable = false, length = 16)
    private String target;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount = 0;

    /** PENDING | SENDING | SENT | FAILED. */
    @Column(nullable = false, length = 16)
    private String status = "SENT";

    /** Kacinci deneme (claim'de artar). */
    @Column(nullable = false)
    private int attempts = 0;

    /** Siradaki deneme zamani; SENDING'te lease bitisi. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Son gonderim hatasi (tani icin). */
    @Column(name = "last_error")
    private String lastError;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public NewsPushLog(Long articleId, String target, int recipientCount) {
        this.articleId = articleId;
        this.target = target;
        this.recipientCount = recipientCount;
    }
}

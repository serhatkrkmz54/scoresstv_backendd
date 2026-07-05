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
 * Bir haberin push edildigini kaydeden idempotency satiri.
 *
 * <p>{@code article_id} UNIQUE — bir haber en fazla BIR kez push edilir.
 * {@link NewsNotificationService} push oncesi bu tablonun varligini kontrol
 * eder; sonrasinda satir yazar. Yeniden yayinla/guncelle tekrar push uretmez.
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

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public NewsPushLog(Long articleId, String target, int recipientCount) {
        this.articleId = articleId;
        this.target = target;
        this.recipientCount = recipientCount;
    }
}

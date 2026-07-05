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
 * Haber denetim gunlugu — kim (actorId) hangi habere (articleId) hangi eylemi
 * (action: CREATE/UPDATE/PUBLISH/UNPUBLISH/DELETE) ne zaman uyguladi.
 */
@Entity
@Table(name = "news_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class NewsAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 32)
    private String action;

    @CreationTimestamp
    @Column(name = "at", updatable = false, nullable = false)
    private Instant at;

    @Column(length = 1024)
    private String meta;

    public NewsAuditLog(Long articleId, Long actorId, String action, String meta) {
        this.articleId = articleId;
        this.actorId = actorId;
        this.action = action;
        this.meta = meta;
    }
}

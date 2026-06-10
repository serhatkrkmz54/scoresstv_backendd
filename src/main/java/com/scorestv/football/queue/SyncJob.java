package com.scorestv.football.queue;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Tek bir sync iş kaydı. RabbitMQ message'inin DB karşılığı.
 *
 * <p>Lifecycle: PENDING → IN_PROGRESS → COMPLETED veya FAILED. Hata olursa
 * tekrar PENDING'e doner ve {@code attempts} artar.
 */
@Entity
@Table(name = "sync_queue")
@Getter
@Setter
@NoArgsConstructor
public class SyncJob extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    private SyncJobType jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    /** 1 = en yuksek, 9 = en dusuk. Worker ORDER BY priority ASC ile alir. */
    @Column(nullable = false)
    private Integer priority = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncJobStatus status = SyncJobStatus.PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}

package com.scorestv.mobile.notify;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepository
        extends JpaRepository<NotificationOutbox, Long> {

    /**
     * Gönderilmeyi bekleyen (PENDING) ve zamanı gelmiş (next_attempt_at &le; now)
     * satırlar — en eski sıradan. Worker bunları işler.
     */
    List<NotificationOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String status, Instant now, Pageable pageable);

    /** Aynı bildirim zaten kuyruğa girmiş mi? (idempotent enqueue). */
    boolean existsByDedupKey(String dedupKey);
}

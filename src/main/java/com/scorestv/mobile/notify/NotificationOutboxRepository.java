package com.scorestv.mobile.notify;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Bu collapse slotu için daha önce satır (PENDING/SENT/FAILED) yazıldı mı?
     * true → yeni gelen aynı slotlu bildirim bir GÜNCELLEME'dir (sessiz gider). */
    boolean existsByCollapseKey(String collapseKey);

    /**
     * Retention temizliği: verilen statüdeki, {@code before}'dan eski satırları
     * siler (tablo sınırsız büyümesin). {@link NotificationOutboxCleanupJob}
     * kullanır. @Modifying → çağıran @Transactional olmalı.
     */
    @Modifying
    @Query("DELETE FROM NotificationOutbox o WHERE o.status = :status AND o.createdAt < :before")
    int deleteByStatusAndCreatedAtBefore(@Param("status") String status,
                                         @Param("before") Instant before);
}

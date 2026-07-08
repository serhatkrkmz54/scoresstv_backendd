package com.scorestv.mobile.broadcast;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** {@link BroadcastNotification} kuyrugu + gecmis kayitlari. */
public interface BroadcastNotificationRepository
        extends JpaRepository<BroadcastNotification, Long> {

    /** En yeni gonderimler once — panel gecmis listesi icin. */
    List<BroadcastNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Gonderilmeyi bekleyen (QUEUED) ve zamani gelmis (next_attempt_at &le; now)
     * satirlar — en eski once. Worker bunlari isler.
     */
    List<BroadcastNotification> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String status, Instant now, Pageable pageable);
}

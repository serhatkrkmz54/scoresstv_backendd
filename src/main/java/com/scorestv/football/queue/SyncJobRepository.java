package com.scorestv.football.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Sync queue erisim. SELECT FOR UPDATE SKIP LOCKED ile race-free claim. */
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    /**
     * Bir sonraki PENDING + zamani gelmis job'u atomic olarak kilitler ve doner.
     * SELECT FOR UPDATE SKIP LOCKED — paralel worker varsa ayni satiri ikinci
     * worker atlar ve bir sonrakini alir.
     *
     * <p>{@code Pageable} ile LIMIT 1 saglariz; Hibernate native lock hint
     * SKIP_LOCKED ile Postgres pg-statement uretir.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout", value = "-2"))  // SKIP LOCKED
    @Query("SELECT j FROM SyncJob j "
            + "WHERE j.status = com.scorestv.football.queue.SyncJobStatus.PENDING "
            + "  AND j.nextAttemptAt <= :now "
            + "  AND j.priority <= :maxPriority "
            + "ORDER BY j.priority ASC, j.nextAttemptAt ASC")
    List<SyncJob> findClaimable(@Param("now") Instant now,
                                @Param("maxPriority") int maxPriority,
                                Pageable pageable);

    /** Belirli bir status icin sayim. Admin dashboard. */
    @Query("SELECT COUNT(j) FROM SyncJob j WHERE j.status = :status")
    long countByStatus(@Param("status") SyncJobStatus status);

    /** Belirli job_type + status sayim. */
    @Query("SELECT COUNT(j) FROM SyncJob j "
            + "WHERE j.jobType = :type AND j.status = :status")
    long countByTypeAndStatus(@Param("type") SyncJobType type,
                              @Param("status") SyncJobStatus status);

    /** Duplicate enqueue kontrol: ayni (type, payload string'i) PENDING var mi? */
    @Query(value = "SELECT EXISTS("
            + "SELECT 1 FROM sync_queue "
            + "WHERE job_type = :type AND status = 'PENDING' "
            + "  AND payload = CAST(:payloadJson AS jsonb))",
            nativeQuery = true)
    boolean existsPending(@Param("type") String type,
                          @Param("payloadJson") String payloadJson);

    /** Failed'leri PENDING'e geri al — admin retry endpoint icin. */
    @Modifying
    @Query("UPDATE SyncJob j SET j.status = com.scorestv.football.queue.SyncJobStatus.PENDING, "
            + "j.attempts = 0, j.nextAttemptAt = CURRENT_TIMESTAMP, j.lastError = null "
            + "WHERE j.status = com.scorestv.football.queue.SyncJobStatus.FAILED")
    int retryAllFailed();

    /** Eski COMPLETED satirlari sil — bakim icin. */
    @Modifying
    @Query("DELETE FROM SyncJob j "
            + "WHERE j.status IN ("
            + "  com.scorestv.football.queue.SyncJobStatus.COMPLETED, "
            + "  com.scorestv.football.queue.SyncJobStatus.FAILED) "
            + "  AND j.updatedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}

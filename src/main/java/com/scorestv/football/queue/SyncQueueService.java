package com.scorestv.football.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Producer API — herhangi bir yerden sync job kuyruga atilir.
 *
 * <p>Kullanim:
 * <pre>{@code
 *   syncQueueService.enqueue(SyncJobType.TEAM_SQUAD_SYNC,
 *       Map.of("teamId", 549L, "season", 2025), Priority.BULK);
 * }</pre>
 *
 * <p>Duplicate koruma: {@code enqueueIfAbsent} ayni (type, payload) PENDING
 * varsa eklemez. {@code enqueue} her zaman ekler — duplicate kabul edilebilirse
 * (orn. force refresh).
 */
@Service
public class SyncQueueService {

    private static final Logger log = LoggerFactory.getLogger(SyncQueueService.class);

    /** Tipik priority degerleri — magic number kullanma. */
    public static final int PRIORITY_URGENT = 1;     // Kullanici tetikledi
    public static final int PRIORITY_LIVE = 2;       // Live ticker / canli mac
    public static final int PRIORITY_COVERED = 3;    // Covered takim/oyuncu refresh
    public static final int PRIORITY_DEFAULT = 5;    // Admin manuel
    public static final int PRIORITY_BULK = 7;       // Bulk operations (squad dump)
    public static final int PRIORITY_LOW = 9;        // Tarihsel arsiv

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SyncJobRepository repository;

    public SyncQueueService(SyncJobRepository repository) {
        this.repository = repository;
    }

    /**
     * Job'u kuyruga ekler. Duplicate kontrolu YAPMAZ — caller emin olmali.
     * Ornek kullanim: force refresh, debug.
     */
    @Transactional
    public SyncJob enqueue(SyncJobType type, Map<String, Object> payload, int priority) {
        return enqueueAt(type, payload, priority, Instant.now());
    }

    /** Belirli zamandan sonra calismak uzere planlanmis job. */
    @Transactional
    public SyncJob enqueueAt(SyncJobType type, Map<String, Object> payload,
                              int priority, Instant runAt) {
        SyncJob job = new SyncJob();
        job.setJobType(type);
        job.setPayload(payload);
        job.setPriority(priority);
        job.setStatus(SyncJobStatus.PENDING);
        job.setNextAttemptAt(runAt);
        return repository.save(job);
    }

    /**
     * Job'u kuyruga ekler — ayni (type, payload) icin PENDING varsa
     * <i>eklemez</i>. Race condition'a karsi DB-level idempotency.
     *
     * @return true → eklendi; false → zaten var
     */
    @Transactional
    public boolean enqueueIfAbsent(SyncJobType type, Map<String, Object> payload,
                                    int priority) {
        String payloadJson;
        try {
            payloadJson = JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Payload JSON encode hatasi: {}", ex.getMessage());
            return false;
        }
        if (repository.existsPending(type.name(), payloadJson)) {
            return false;
        }
        enqueue(type, payload, priority);
        return true;
    }

    /** Su an PENDING job sayisi — surekli tazelik supuruculugu kuyruk-derinligi
     *  freni icin kullanir (worker'dan hizli is basip backlog sismesin). */
    public long pendingCount() {
        return repository.countByStatus(SyncJobStatus.PENDING);
    }

    /**
     * Eski terminal (COMPLETED/FAILED) job'lari siler — tablo sismesin diye.
     * {@code AutoEnqueueScheduler} gunluk cagirir. PENDING/IN_PROGRESS dokunulmaz,
     * yani isleyen kuyruk etkilenmez. Tablo kucuk kalinca dedup/claim sorgulari
     * hizli kalir (autovacuum da rahatlar).
     *
     * @param retentionDays bu kadar gunden ESKI terminal job'lar silinir
     * @return silinen satir sayisi
     */
    @Transactional
    public int cleanupOlderThan(int retentionDays) {
        Instant before = Instant.now().minus(Duration.ofDays(Math.max(1, retentionDays)));
        int deleted = repository.deleteOlderThan(before);
        if (deleted > 0) {
            log.info("Sync queue temizligi: {} eski COMPLETED/FAILED job silindi (> {} gun)",
                    deleted, retentionDays);
        }
        return deleted;
    }

    /** Bulk enqueue — coklu job'u tek tx'te ekler (transactional batch). */
    @Transactional
    public int enqueueAll(SyncJobType type, java.util.Collection<Map<String, Object>> payloads,
                          int priority) {
        int added = 0;
        for (Map<String, Object> payload : payloads) {
            if (enqueueIfAbsent(type, payload, priority)) added++;
        }
        return added;
    }
}

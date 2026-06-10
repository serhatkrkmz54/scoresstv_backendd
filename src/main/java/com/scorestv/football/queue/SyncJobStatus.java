package com.scorestv.football.queue;

/**
 * Sync job durum makinasi:
 * <pre>
 *   PENDING ──(claim)──► IN_PROGRESS ──(ok)─────► COMPLETED
 *      ▲                      │
 *      │                      ├──(rate limit)──► PENDING (next_attempt +5dk)
 *      │                      │
 *      └────(retry backoff)───┴──(error)───────► PENDING (attempts++) veya FAILED (attempts>5)
 * </pre>
 */
public enum SyncJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

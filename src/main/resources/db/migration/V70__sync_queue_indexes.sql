-- ============================================================
-- sync_queue performans index'leri + sismislige karsi.
--
-- sync_queue ~1M satira ulasinca dedup (existsPending) ve worker claim
-- (findClaimable) sorgulari TAM TABLO TARAMASI yapip Postgres CPU'sunu bir
-- cekirdek dolduruyordu (eski COMPLETED/FAILED satirlar hic silinmedigi icin
-- tablo siserek). Asagidaki KISMI (partial) index'ler yalniz PENDING satirlari
-- kapsar → kucuk kalir; dedup/claim O(log n) olur, COMPLETED birikse de etkilenmez.
--
-- NOT: CONCURRENTLY KULLANILMADI — Flyway migration'i bir transaction icinde
-- calistirir, Postgres CONCURRENTLY'yi tx icinde reddeder. Prod'da index zaten
-- elle (CONCURRENTLY) olusturulduysa IF NOT EXISTS sayesinde no-op gecer; bos/
-- yeni DB'de tablo kucuk oldugu icin aninda olusur.
-- ============================================================

-- existsPending dedup: WHERE job_type=? AND status='PENDING' AND payload=?::jsonb
CREATE INDEX IF NOT EXISTS idx_sync_queue_pending_dedup
    ON sync_queue (job_type, payload)
    WHERE status = 'PENDING';

-- findClaimable: WHERE status='PENDING' AND next_attempt_at<=? AND priority<=?
--                ORDER BY priority ASC, next_attempt_at ASC
CREATE INDEX IF NOT EXISTS idx_sync_queue_claim
    ON sync_queue (priority, next_attempt_at)
    WHERE status = 'PENDING';

-- deleteOlderThan bakim sorgusu: WHERE status IN ('COMPLETED','FAILED') AND updated_at < ?
CREATE INDEX IF NOT EXISTS idx_sync_queue_cleanup
    ON sync_queue (status, updated_at);

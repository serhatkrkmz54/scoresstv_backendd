-- DB tabanli sync job queue. RabbitMQ alternatifi — bizim olcegimizde yeterli.
--
-- Pattern:
-- 1) Producer (admin endpoint veya cron) sync_queue'ya satir INSERT eder
-- 2) SyncQueueWorker @Scheduled fixedDelay=2sn ile claimNextPending() cagirir
-- 3) Worker SELECT FOR UPDATE SKIP LOCKED ile atomic claim — race-free
-- 4) Job execute edilir → status COMPLETED veya FAILED
-- 5) RateLimitException olursa next_attempt_at ileri tarihe atilir (5dk)
--
-- Throughput: 2sn'de 1 is = 30/dk = 43.2k/gun. Ultra plan 75k limiti icinde
-- live ticker + lazy sync + scheduled job'lara yer kalir.
CREATE TABLE IF NOT EXISTS sync_queue (
    id              BIGSERIAL    PRIMARY KEY,
    -- "TEAM_SQUAD_SYNC", "PLAYER_PROFILE_SYNC", "LEAGUE_PLAYERS_DUMP" vb.
    job_type        VARCHAR(50)  NOT NULL,
    -- JSON payload: {teamId: 549, season: 2025} gibi job parametreleri
    payload         JSONB        NOT NULL,
    -- 1=acil (kullanici tetikledi), 3=covered, 5=default, 7=bulk, 9=dusuk
    priority        INTEGER      NOT NULL DEFAULT 5,
    -- PENDING | IN_PROGRESS | COMPLETED | FAILED
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    -- Bir sonraki deneme zamani — retry'lar geriye dogru zamanlanir
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Worker queryesi: WHERE status='PENDING' AND next_attempt_at <= NOW()
-- ORDER BY priority, next_attempt_at LIMIT 1. Partial index hizli tarama saglar.
CREATE INDEX IF NOT EXISTS idx_sync_queue_pending
    ON sync_queue (priority, next_attempt_at)
    WHERE status = 'PENDING';

-- Admin dashboard: hangi job type'tan kac PENDING var?
CREATE INDEX IF NOT EXISTS idx_sync_queue_type_status
    ON sync_queue (job_type, status);

-- Eski COMPLETED satirlar icin cleanup query'si (admin endpoint).
CREATE INDEX IF NOT EXISTS idx_sync_queue_completed_at
    ON sync_queue (updated_at)
    WHERE status IN ('COMPLETED', 'FAILED');

-- Duplicate enqueue koruma: ayni (job_type, payload) hash + PENDING durumu
-- icin tek satir. payload jsonb_hash_extended ile dogrudan unique yapamayiz,
-- pratik cozum: producer enqueue oncesi DB'de var olani kontrol etsin.

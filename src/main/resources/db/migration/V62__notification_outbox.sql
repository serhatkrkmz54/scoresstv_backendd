-- Bildirim OUTBOX'ı — GARANTİLİ teslim (at-least-once).
--
-- Maç başladı/bitti bildirimi "claim" ile tam-bir-kez TETİKLENİYOR, ama teslim
-- best-effort'tu (FCM o an hata verirse kaybolurdu). Outbox: tetikleme anında
-- "pending" bir satır yazılır; ayrı bir worker bunları gönderir, başarısızsa
-- backoff'la TEKRAR DENER. FCM hıçkırığı / restart / geçici ağ — bildirimi
-- düşürmez.

CREATE TABLE notification_outbox (
    id              BIGSERIAL    PRIMARY KEY,
    kind            VARCHAR(20)  NOT NULL,                 -- KICKOFF|FINAL|GOAL|EVENT
    notif_type      VARCHAR(20)  NOT NULL,                 -- basladi|bitti|gol|kirmizi|penalti (alıcı tercih tipi)
    fixture_id      BIGINT       NOT NULL,
    team_id         BIGINT,                                -- GOAL/EVENT: ilgili takım; KICKOFF/FINAL: null (iki takım)
    -- Mesaj ENQUEUE anında render edilir (skor/oyuncu snapshot'ı dondurulur);
    -- geç gönderimde içerik değişmez.
    title           VARCHAR(255) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    data_json       TEXT,                                  -- FCM data payload (JSON)
    -- İdempotency: aynı bildirim için tek satır (UNIQUE).
    dedup_key       VARCHAR(120) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING|SENT|FAILED
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_outbox_dedup UNIQUE (dedup_key)
);

-- Worker'ın "gönderilmeyi bekleyen + zamanı gelmiş" satırları hızlı bulması için.
CREATE INDEX ix_notification_outbox_due
    ON notification_outbox (status, next_attempt_at);

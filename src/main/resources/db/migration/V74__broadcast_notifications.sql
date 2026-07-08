-- Genel (habere/maca bagli OLMAYAN) push bildirim kuyrugu + gonderim gecmisi.
-- Admin panelinden serbest metinli bildirim gonderilir; istek aninda QUEUED
-- yazilir, arka plandaki worker GARANTILI (retry + backoff) gonderir.
CREATE TABLE broadcast_notifications (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    link            VARCHAR(1000),                       -- opsiyonel; dokununca acilir
    platform_target VARCHAR(20)   NOT NULL DEFAULT 'ALL', -- ALL | IOS | ANDROID
    lang_target     VARCHAR(20)   NOT NULL DEFAULT 'ALL', -- ALL | TR | EN
    recipient_count INTEGER       NOT NULL DEFAULT 0,      -- hedeflenen cihaz sayisi
    sent_count      INTEGER       NOT NULL DEFAULT 0,      -- FCM basarili gonderim

    -- Garantili teslim durumu (outbox deseniyle ayni)
    status          VARCHAR(20)   NOT NULL DEFAULT 'QUEUED', -- QUEUED | SENT | FAILED
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_error      VARCHAR(500),

    created_by      BIGINT,                                -- gonderen kullanici (users.id)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Worker'in bekleyen isleri hizli bulmasi icin.
CREATE INDEX idx_broadcast_notifications_due
    ON broadcast_notifications (status, next_attempt_at);

CREATE INDEX idx_broadcast_notifications_created_at
    ON broadcast_notifications (created_at DESC);

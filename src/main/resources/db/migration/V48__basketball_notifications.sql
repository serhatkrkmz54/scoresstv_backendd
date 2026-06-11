-- Basketbol bildirim altyapısı (football'dan ayrı):
--   1) Cihaz-favori basketbol maçı abonelikleri (FCM recipient kaynağı)
--   2) basketball_games üzerinde push idempotency durum kolonları

-- 1) Favori basketbol maçı abonelikleri — device_match_subscriptions'ın
--    basketbol karşılığı (fixture_id yerine basketball_game_id).
CREATE TABLE device_basketball_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    basketball_game_id BIGINT NOT NULL
        REFERENCES basketball_games(id) ON DELETE CASCADE,
    -- BaseEntity audit kolonları
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, basketball_game_id)
);

-- Dispatcher için: "bu maçın recipient'ları kim?" sorgusu.
CREATE INDEX idx_device_bk_subs_game
    ON device_basketball_subscriptions (basketball_game_id);

-- Cihaz bazlı replace-sync (eski set silme) için.
CREATE INDEX idx_device_bk_subs_device
    ON device_basketball_subscriptions (device_token_id);

-- 2) Push idempotency durum kolonları — aynı geçiş için tekrar bildirim
--    gönderilmesin (restart/re-sync dahil).
ALTER TABLE basketball_games
    ADD COLUMN notified_start BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notified_final BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_notified_period SMALLINT NOT NULL DEFAULT 0;

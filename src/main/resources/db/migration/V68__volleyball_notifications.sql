-- ============================================================
-- Voleybol bildirim altyapisi (football/basketball'dan ayri):
--   1) Cihaz-favori voleybol maci abonelikleri (FCM recipient kaynagi)
--   2) Cihaz bazli takim bildirim tercihleri (3 olay: basladi/set/bitti)
--
-- (push idempotency durum kolonlari notified_start/notified_final/
--  last_notified_period zaten V66'da volleyball_games icinde olusturuldu.)
-- ============================================================

-- 1) Favori voleybol maci abonelikleri.
CREATE TABLE device_volleyball_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    volleyball_game_id BIGINT NOT NULL
        REFERENCES volleyball_games(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, volleyball_game_id)
);
CREATE INDEX idx_device_vb_subs_game
    ON device_volleyball_subscriptions (volleyball_game_id);
CREATE INDEX idx_device_vb_subs_device
    ON device_volleyball_subscriptions (device_token_id);

-- 2) Cihaz bazli voleybol takim bildirim tercihleri.
--   basladi  -> notify_start    (mac basladi / S1)
--   set      -> notify_period   (set bitti)
--   bitti    -> notify_final    (FT / AW)
CREATE TABLE volleyball_notification_prefs (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL
        REFERENCES volleyball_teams(id) ON DELETE CASCADE,
    notify_start BOOLEAN NOT NULL DEFAULT TRUE,
    notify_period BOOLEAN NOT NULL DEFAULT TRUE,
    notify_final BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, team_id)
);
CREATE INDEX idx_vb_notif_prefs_team ON volleyball_notification_prefs (team_id);
CREATE INDEX idx_vb_notif_prefs_device ON volleyball_notification_prefs (device_token_id);

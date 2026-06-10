-- V40 — Cihaz bazli, takim bazli bildirim tercihleri.
--
-- Her satir = bir cihaz icin bir takimin bildirim ayarlarini tutar.
-- Cihaz silinirse (token gecersiz) cascade ile prefs'leri de silinir.
--
-- Mobile DTO key mapping:
--   gol      → notify_goal
--   kirmizi  → notify_red_card
--   penalti  → notify_penalty
--   basladi  → notify_kickoff
--   bitti    → notify_final
--
-- PUT /api/v1/mobile/notification-prefs endpoint'i batch upsert yapar:
-- mobile prefs degistiginde tum harita gonderilir, backend replace eder.

CREATE TABLE user_notification_prefs (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL
        REFERENCES teams(id) ON DELETE CASCADE,
    notify_goal BOOLEAN NOT NULL DEFAULT TRUE,
    notify_red_card BOOLEAN NOT NULL DEFAULT TRUE,
    notify_penalty BOOLEAN NOT NULL DEFAULT TRUE,
    notify_kickoff BOOLEAN NOT NULL DEFAULT TRUE,
    notify_final BOOLEAN NOT NULL DEFAULT TRUE,
    -- BaseEntity audit kolonlari
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, team_id)
);

-- Dispatcher icin: "bu takim icin bildirim isteyen tum cihazlar" sorgusu.
-- Olay bir takimda olusunca buradan FCM token listesi cikariliriz.
CREATE INDEX idx_user_notif_prefs_team ON user_notification_prefs (team_id);

-- Cihaz prefs'i okumak icin (mobile -> backend sync getter).
CREATE INDEX idx_user_notif_prefs_device ON user_notification_prefs (device_token_id);

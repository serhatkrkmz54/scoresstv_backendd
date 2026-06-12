-- V51 — A-Faz5: Cihaz bazli BASKETBOL takim bildirim tercihleri.
--
-- Futbol V40 (user_notification_prefs) ile aynı pattern, basketbola adapte:
--   - 5 olay tipi yerine 3 (basketbolda gol/kart/penalti yok)
--   - team_id basketball_teams'e FK
--
-- Mobile DTO key mapping (BasketballNotificationPrefs):
--   basladi  → notify_start    (tip-off / Q1 başladı)
--   ceyrek   → notify_period   (Q1/Q2/Q3 sonu + HT)
--   bitti    → notify_final    (FT / AOT)
--
-- PUT /api/v1/mobile/basketball/notification-prefs endpoint'i batch upsert
-- yapar; futbolla aynı sözleşme — mobile prefs değiştiğinde tüm harita
-- gönderilir, backend replace eder.

CREATE TABLE basketball_notification_prefs (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    team_id BIGINT NOT NULL
        REFERENCES basketball_teams(id) ON DELETE CASCADE,
    notify_start BOOLEAN NOT NULL DEFAULT TRUE,
    notify_period BOOLEAN NOT NULL DEFAULT TRUE,
    notify_final BOOLEAN NOT NULL DEFAULT TRUE,
    -- BaseEntity audit kolonları
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, team_id)
);

-- Dispatcher icin: "bu basketbol takimi icin bildirim isteyen tum cihazlar"
-- sorgusu. Olay olustugunda buradan FCM token listesi cikariliriz.
CREATE INDEX idx_bk_notif_prefs_team ON basketball_notification_prefs (team_id);

-- Cihaz prefs'i okumak icin (mobile -> backend sync getter).
CREATE INDEX idx_bk_notif_prefs_device ON basketball_notification_prefs (device_token_id);

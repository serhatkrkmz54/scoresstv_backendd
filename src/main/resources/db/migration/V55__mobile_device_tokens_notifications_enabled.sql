-- Cihaz basina master bildirim toggle. Default TRUE (acik). Kullanici
-- profil > "Bildirimler" satirindan kapatinca NotificationDispatcher push
-- gondermeden once filter uygular.
ALTER TABLE mobile_device_tokens
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_mobile_device_tokens_notif_enabled
    ON mobile_device_tokens (notifications_enabled) WHERE notifications_enabled = TRUE;

-- ScoresTV V77 — Bildirim OUTBOX: collapse/replace (iki fazlı canlı bildirim).
--
-- Amaç: gol/kırmızı kart önce ISIMSIZ (anında) gider, oyuncu adı gelince AYNI
-- bildirimi YERINDE günceller (Maçkolik deseni). collapse_key OS bildirim
-- slotudur (Android notification tag / APNs apns-collapse-id); aynı anahtarla
-- gelen ikinci bildirim eskisini değiştirir, alt alta yığmaz. silent=true olan
-- güncellemeler sessiz gider (kullanıcıyı ikinci kez titretmesin).

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS collapse_key VARCHAR(120);

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS silent BOOLEAN NOT NULL DEFAULT FALSE;

-- Aynı collapse slotu için önceki satır var mı? (sessiz-güncelleme kararı)
CREATE INDEX IF NOT EXISTS ix_notification_outbox_collapse
    ON notification_outbox (collapse_key);

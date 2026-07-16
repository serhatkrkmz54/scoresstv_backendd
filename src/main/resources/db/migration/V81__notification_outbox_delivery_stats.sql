-- ScoresTV V81 — Bildirim OUTBOX: teslim istatistikleri (admin panelde takip).
--
-- Gönderim anında worker bu alanları doldurur; admin "Bildirim Gönderimleri"
-- sayfası bunları listeler. send_mode = hangi yol (TOKEN/TOPIC/DUAL), recipients
-- = token yolunda hedeflenen cihaz sayısı (topic fan-out FCM'de görünmez → 0),
-- delivered_count = FCM'in başarılı bildirdiği sayı, sent_at = gönderim anı.

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS send_mode VARCHAR(12);

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS recipients INTEGER;

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS delivered_count INTEGER;

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;

-- Admin listesi son gönderimleri created_at DESC ile çeker.
CREATE INDEX IF NOT EXISTS ix_notification_outbox_created_desc
    ON notification_outbox (created_at DESC);

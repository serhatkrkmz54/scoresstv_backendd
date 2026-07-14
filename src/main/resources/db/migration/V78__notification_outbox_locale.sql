-- ScoresTV V78 — Bildirim OUTBOX: TR + EN yerelleştirme.
--
-- Mevcut title/body = TR (varsayılan, geriye dönük). title_en/body_en = EN.
-- Gönderim anında alıcılar cihaz locale'ine göre (MobileDeviceToken.locale)
-- ayrılır; "tr" cihazlar TR metni, diğerleri EN metni alır. Eski satırlarda
-- EN null olabilir → gönderimde TR'ye düşülür (fallback).

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS title_en VARCHAR(255);

ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS body_en VARCHAR(500);

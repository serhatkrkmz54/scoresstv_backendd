-- "İlk yarı bitti" (HT) ve "İkinci yarı başladı" (2H) bildirim tercihleri.
-- Diğer toggle'larla tutarlı: varsayılan AÇIK (true) — takımı takip edenler
-- bu bildirimleri de alır, kullanıcı ayarlardan kapatabilir. Mevcut satırlar
-- ADD COLUMN ... DEFAULT TRUE ile otomatik true backfill olur.

ALTER TABLE user_notification_prefs
    ADD COLUMN notify_halftime    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_second_half BOOLEAN NOT NULL DEFAULT TRUE;

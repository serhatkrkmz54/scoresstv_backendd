-- İlk 11 (kadro) açıklandı bildirimi tercihi.
-- Mevcut takip kayıtları için varsayılan AÇIK (diğer bildirim tipleriyle tutarlı).
ALTER TABLE user_notification_prefs
    ADD COLUMN notify_lineup boolean NOT NULL DEFAULT true;

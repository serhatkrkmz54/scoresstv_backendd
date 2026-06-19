-- Faz 3: notification_outbox retention/temizlik için index.
--
-- NotificationOutboxCleanupJob, SENT/FAILED satırları YAŞA göre (created_at)
-- siler ki tablo sınırsız büyümesin. Bu index silme sorgusunu hızlandırır.
-- Mevcut ix_notification_outbox_due (status, next_attempt_at) worker içindir;
-- temizlik farklı erişim deseni (status, created_at) kullanır.
CREATE INDEX IF NOT EXISTS ix_notification_outbox_status_created
    ON notification_outbox (status, created_at);

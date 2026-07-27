-- Haber push'u transactional OUTBOX'a tasinir: satir, yayinlama transaction'i
-- ile ATOMIK yazilir (status=PENDING); ayri worker atomik claim + lease ile
-- gonderir, hata durumunda backoff'la tekrar dener. Boylece:
--   * cift gonderim imkansiz (UNIQUE article_id + atomik claim),
--   * sunucu cokse bile push kaybolmaz (satir DB'de, worker devralir),
--   * FCM gecici hatalari bildirimi dusurmez (retry + backoff).
-- Mevcut satirlar zaten gonderilmis → DEFAULT 'SENT'.
ALTER TABLE news_push_log ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'SENT';
ALTER TABLE news_push_log ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;
ALTER TABLE news_push_log ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE news_push_log ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_news_push_log_due
    ON news_push_log (status, next_attempt_at);

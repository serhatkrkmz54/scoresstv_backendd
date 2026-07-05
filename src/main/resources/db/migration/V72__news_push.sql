-- Haber (news) PUSH bildirim altyapisi (FAZ 4a).
--
-- Editor bir haberi yayinlarken "push gonder" secerse, kullanicilara FCM push
-- gonderilir. Iki hedefleme:
--   1) ALL       -> haberin diliyle (lang) eslesen tum bildirimi acik cihazlar.
--   2) FAVORITES -> habere bagli takim/lig/oyuncu/ulke takipcisi cihazlar.
--
-- Global haber opt-out (mobil toggle sonraki fazda bu kolonu degistirir).
-- Mevcut cihazlar icin ACIK (diger notify_* kolonlariyla tutarli varsayilan).
ALTER TABLE mobile_device_tokens
    ADD COLUMN notify_news boolean NOT NULL DEFAULT true;

-- Idempotency: bir haber en fazla BIR kez push edilir. UNIQUE(article_id) ile
-- ayni haberin tekrar tetiklenmesi (yeniden yayinla vb.) yeni push uretmez.
CREATE TABLE news_push_log (
    id              BIGSERIAL   PRIMARY KEY,
    article_id      BIGINT      NOT NULL,
    target          VARCHAR(16) NOT NULL,
    recipient_count INT         NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_news_push_log_article UNIQUE (article_id)
);

-- Kullanıcı veri katkıları (hata bildirimi / öneri) — "Öneri → Doğrulama" hattı.
-- TAMAMEN AYRI tablo: API'den senkronlanan verilere (fixtures/teams/...) hiçbir
-- etkisi yok. Onay yalnız bu kaydı işaretler + Scores Puanı verir; asıl veri
-- düzeltmesi (varsa) editör tarafından ayrıca yapılır.
CREATE TABLE data_contributions (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    sport           VARCHAR(12)   NOT NULL DEFAULT 'football',
    -- SCORE | STATUS | LINEUP | TV_CHANNEL | NAME | MISSING_DATA | OTHER
    type            VARCHAR(30)   NOT NULL,
    -- FIXTURE | TEAM | LEAGUE | PLAYER | OTHER
    target_type     VARCHAR(20)   NOT NULL,
    target_id       BIGINT,
    -- İnsan-okur hedef etiketi (or. "Fenerbahçe - Galatasaray") — panel listesi
    -- entity join yapmadan gösterebilsin diye gönderimde dondurulur.
    target_label    VARCHAR(200),
    message         VARCHAR(1000) NOT NULL,
    suggested_value VARCHAR(500),
    -- PENDING | APPROVED | REJECTED
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    reviewed_by     BIGINT        REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    review_note     VARCHAR(500),
    points_awarded  INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Panel kuyruğu (durum + tarih) ve kullanıcı geçmişi/limit sorguları.
CREATE INDEX idx_contrib_status_created ON data_contributions(status, created_at DESC);
CREATE INDEX idx_contrib_user_created ON data_contributions(user_id, created_at DESC);

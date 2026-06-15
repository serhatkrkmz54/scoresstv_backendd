-- İletişim formundan gelen mesajlar.
-- Public POST /api/v1/contact ile yazılır; admin /api/v1/admin/contact ile listeler/yönetir.
CREATE TABLE contact_messages (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    email       VARCHAR(180) NOT NULL,
    subject     VARCHAR(160),
    message     TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Admin listesi: duruma göre filtre + en yeni önce.
CREATE INDEX idx_contact_messages_status_created
    ON contact_messages (status, created_at DESC);

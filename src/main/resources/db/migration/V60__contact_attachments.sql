-- "Bize Ulaşın" / sorun bildirimi: kaynak ayrımı (web/mobil) + resim/video ekleri.

-- Mesajın geldiği kanal (web formu mu, mobil "Bize Ulaşın" mı).
ALTER TABLE contact_messages
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'web';

-- Bir mesaja bağlı resim/video ekleri. Dosyalar MinIO'ya yüklenir; burada
-- yalnız anahtar + herkese açık URL + meta tutulur. Mesaj silinince ekler de
-- silinir (ON DELETE CASCADE).
CREATE TABLE contact_attachment (
    id            BIGSERIAL PRIMARY KEY,
    message_id    BIGINT       NOT NULL REFERENCES contact_messages(id) ON DELETE CASCADE,
    storage_key   VARCHAR(255) NOT NULL,
    url           VARCHAR(600) NOT NULL,
    content_type  VARCHAR(100),
    file_size     BIGINT,
    original_name VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_contact_attachment_message ON contact_attachment (message_id);

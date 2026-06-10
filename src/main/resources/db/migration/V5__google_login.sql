-- ===================================================================
-- V5 - Google ile giris destegi
-- - password artik null olabilir (yalnizca Google ile olusturulan hesaplar).
-- - google_id: Google 'sub' degeri, benzersiz.
-- ===================================================================

ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users ADD COLUMN google_id VARCHAR(64);
ALTER TABLE users ADD CONSTRAINT uq_users_google_id UNIQUE (google_id);

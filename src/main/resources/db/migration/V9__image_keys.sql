-- ===================================================================
-- V9 - Aynalanan görsellerin MinIO nesne anahtarları
--
-- logo_url / flag_url kolonları API-Football'un kaynak adresini tutmaya
-- devam eder. Görsel MinIO'ya aynalandığında ilgili *_key kolonu dolar;
-- serving katmanı key varsa kendi (MinIO/CDN) URL'ini, yoksa kaynak URL'i verir.
-- ===================================================================

ALTER TABLE teams     ADD COLUMN logo_key VARCHAR(255);
ALTER TABLE leagues   ADD COLUMN logo_key VARCHAR(255);
ALTER TABLE countries ADD COLUMN flag_key VARCHAR(255);

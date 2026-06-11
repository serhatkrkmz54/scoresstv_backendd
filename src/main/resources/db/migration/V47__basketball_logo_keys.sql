-- ============================================================
-- Basketbol image mirror — logo/bayrak MinIO/CDN anahtarları (football gibi).
-- Aynalama servisi API logosunu indirip MinIO'ya koyar, key'i buraya yazar;
-- serving CDN URL'ini bu key'den kurar (yoksa API URL'ine düşer).
-- ============================================================

ALTER TABLE basketball_teams   ADD COLUMN logo_key         VARCHAR(255);
ALTER TABLE basketball_leagues ADD COLUMN logo_key         VARCHAR(255);
ALTER TABLE basketball_leagues ADD COLUMN country_flag_key VARCHAR(255);

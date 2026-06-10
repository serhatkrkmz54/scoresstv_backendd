-- ===================================================================
-- V4 - Kullanici profili alanlari: dogum tarihi + ulke
-- Mevcut satirlar (orn. seed admin) icin null kalir; kayit sirasinda
-- yeni kullanicilardan zorunlu olarak istenir.
-- ===================================================================

ALTER TABLE users
    ADD COLUMN birth_date DATE,
    ADD COLUMN country    VARCHAR(100);

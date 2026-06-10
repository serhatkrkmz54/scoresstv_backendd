-- ===================================================================
-- V8 - fixtures tablosuna status_extra kolonu (uzatma dakikası, 90+X)
--
-- Not: Bu kolon başta V6'ya eklenmişti; ancak V6 bazı veritabanlarına
-- zaten uygulandığından, uygulanmış bir migration değiştirilemez kuralı
-- gereği değişiklik bu ayrı migration'a taşındı.
-- ===================================================================

ALTER TABLE fixtures ADD COLUMN status_extra INTEGER;

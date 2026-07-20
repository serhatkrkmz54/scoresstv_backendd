-- ScoresTV V87 — Kullanici profil resmi (avatar).
-- Kullanicinin yukledigi avatar MinIO'da saklanir; burada yalnizca nesne
-- anahtari (object key) tutulur. Herkese acik URL runtime'da publicUrl(key)
-- ile turetilir. NULL = avatar yok (istemci ad bas harflerini gosterir).
ALTER TABLE users ADD COLUMN avatar_key VARCHAR(255);

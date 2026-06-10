-- ===================================================================
-- V10 - Varlık adlarının elle girilen Türkçe karşılıkları (name_tr)
--
-- API-Football verisi İngilizce gelir; "name" kolonu daima kaynak (İngilizce)
-- adı tutar. "name_tr" ADMIN tarafından elle (Excel ile toplu veya tekil
-- endpoint ile) doldurulur ve senkron tarafından ASLA ezilmez — upsert
-- yalnızca API alanlarını yazar, name_tr'ye dokunmaz.
--
-- Serving katmanı: istek dili "tr" ise name_tr (doluysa) verilir; boşsa
-- İngilizce "name" değerine düşülür. Böylece çeviri kademeli yapılabilir.
-- ===================================================================

ALTER TABLE countries ADD COLUMN name_tr VARCHAR(100);
ALTER TABLE leagues   ADD COLUMN name_tr VARCHAR(150);
ALTER TABLE teams     ADD COLUMN name_tr VARCHAR(150);
ALTER TABLE venues    ADD COLUMN name_tr VARCHAR(150);

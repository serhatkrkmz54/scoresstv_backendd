-- Venue id=0 çakışması temizliği.
--
-- API-Football alt lig stadyumlarına venue.id=0 (bazen ≤0) verir — GEÇERLİ bir
-- kimlik DEĞİL. Eski upsert bunu gerçek id sanıp id=0'lı TÜM stadyumları tek
-- venues(0) satırına çökertip birbirini ezmişti: bir maça başka ülkenin
-- stadyumu görünüyordu (ör. Türkiye ligi maçında Brezilya stadyumu).
--
-- Kaynak düzeltmesi (FixtureUpserter.upsertVenue) artık id<=0'ı Venue entity'sine
-- BAĞLAMIYOR; ekran fixture'ın kendi venueName/venueCity (per-fixture, doğru)
-- fallback'ini kullanır. Burada MEVCUT kirli veri temizlenir.
--
-- fixtures.venue_id ve teams.venue_id → venues(id) FK'leri ON DELETE SET NULL;
-- yine de açıkça null'lıyoruz (okunur + garanti):
--   1) id=0'a bağlı fikstür ve takımların venue_id'sini NULL yap
--      → doğru per-fixture venueName/venueCity fallback'i devreye girer.
--   2) Kirlenmiş venues(0) satırını sil.

UPDATE fixtures SET venue_id = NULL WHERE venue_id = 0;
UPDATE teams    SET venue_id = NULL WHERE venue_id = 0;
DELETE FROM venues WHERE id = 0;

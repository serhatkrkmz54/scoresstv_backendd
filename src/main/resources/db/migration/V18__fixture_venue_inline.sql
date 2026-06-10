-- Fixture'a inline venue alanlari (fallback): API venue.id null
-- gondererek ad+sehir verirse FK kullanamiyoruz; bu kolonlar dogrudan
-- fikstur satirinda saklar.
-- Tipik kullanim: UEFA Champions League / Avrupa Ligi gibi degisik
-- stadlarda oynanan turnuvalar. API tani(ma)digi stadyumlar icin id yok.

ALTER TABLE fixtures
    ADD COLUMN IF NOT EXISTS venue_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS venue_city VARCHAR(100);

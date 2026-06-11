-- ============================================================
-- Basketbol referans verisi: ülkeler + lig güncel sezon alanı.
-- /countries ve /leagues seed'i bu tabloları doldurur.
-- ============================================================

CREATE TABLE basketball_countries (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(10),
    flag        VARCHAR(400),
    updated_at  TIMESTAMPTZ
);

-- Ligin güncel sezonu (teams/standings çekmek için referans).
ALTER TABLE basketball_leagues ADD COLUMN current_season VARCHAR(20);

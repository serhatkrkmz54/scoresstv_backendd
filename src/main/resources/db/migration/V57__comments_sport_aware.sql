-- Yorumları sport-aware yap: futbol + basketbol tek tabloda.
-- fixture_id artık genel "match id" (futbol fixture id VEYA basketbol game id);
-- sport kolonu yorumun hangi spora ait olduğunu belirtir.

-- 1) sport kolonu — önce nullable ekle, mevcut satırları FOOTBALL ile doldur,
--    sonra NOT NULL yap.
ALTER TABLE fixture_comments ADD COLUMN IF NOT EXISTS sport VARCHAR(20);
UPDATE fixture_comments SET sport = 'FOOTBALL' WHERE sport IS NULL;
ALTER TABLE fixture_comments ALTER COLUMN sport SET NOT NULL;

-- 2) Futbol Fixture foreign key'ini kaldır ki basketbol maç id'leri de
--    fixture_id kolonunda saklanabilsin. Kısıt adı otomatik üretildiği için
--    pg_constraint üzerinden bulup düşürüyoruz (yoksa no-op).
DO $$
DECLARE fk text;
BEGIN
  SELECT con.conname INTO fk
  FROM pg_constraint con
  JOIN pg_attribute att
    ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
  WHERE con.conrelid = 'fixture_comments'::regclass
    AND con.contype = 'f'
    AND att.attname = 'fixture_id';
  IF fk IS NOT NULL THEN
    EXECUTE 'ALTER TABLE fixture_comments DROP CONSTRAINT ' || quote_ident(fk);
  END IF;
END $$;

-- 3) Liste sorguları (sport + fixture_id) üzerinden gidiyor — composite index.
CREATE INDEX IF NOT EXISTS idx_fixture_comments_sport_match
  ON fixture_comments (sport, fixture_id);

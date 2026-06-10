-- Yorum sistemi v2: yanit (reply / thread) destegi.
-- parent_id NULL ise top-level yorum; dolu ise baska yorumun yanitı.
-- Maksimum 1 seviye thread (Maçkolik tarzı) — child'a yanit yine parent'i hedefler.

ALTER TABLE fixture_comments
    ADD COLUMN parent_id BIGINT REFERENCES fixture_comments(id) ON DELETE CASCADE;

-- Top-level yorumlar — feed sorgusunu hizlandirir.
CREATE INDEX idx_fixture_comments_parent_null
    ON fixture_comments(fixture_id, created_at DESC)
    WHERE deleted = FALSE AND parent_id IS NULL;

-- Bir yorumun cocuk yanitlari (reply listesi)
CREATE INDEX idx_fixture_comments_parent
    ON fixture_comments(parent_id, created_at ASC)
    WHERE deleted = FALSE;

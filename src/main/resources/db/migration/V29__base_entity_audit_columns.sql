-- BaseEntity'den extend eden entity'lerin tablolarinda eksik audit kolonlarini
-- (created_at + updated_at) tamamlar. V22-V27 migration'larinda bazi tablolar
-- bu kolonlari icermedigi icin Hibernate schema validation "missing column"
-- hatasi veriyordu:
--   - coach_career: ikisi de yok
--   - transfers: updated_at yok
--   - coach_trophies: updated_at yok
--   - player_sidelined: updated_at yok
--
-- Hepsi BaseEntity'den geldigi icin {@code @CreationTimestamp /
-- @UpdateTimestamp} bekliyor; ekleme sonrasinda mevcut satirlar NOW() ile
-- tamamlanir.

-- coach_career: hem created hem updated
ALTER TABLE coach_career
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- transfers: yalniz updated_at eksik
ALTER TABLE transfers
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- coach_trophies: yalniz updated_at eksik
ALTER TABLE coach_trophies
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- player_sidelined: yalniz updated_at eksik
ALTER TABLE player_sidelined
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

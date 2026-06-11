-- ============================================================
-- Basketbol TR ad kolonları — football'daki gibi manuel/editör girişi.
-- Sync ASLA dokunmaz; TR locale'de gösterilir, boşsa İngilizce ada düşülür.
-- ============================================================

ALTER TABLE basketball_teams      ADD COLUMN name_tr         VARCHAR(160);
ALTER TABLE basketball_leagues    ADD COLUMN name_tr         VARCHAR(160);
ALTER TABLE basketball_leagues    ADD COLUMN country_name_tr VARCHAR(120);
ALTER TABLE basketball_countries  ADD COLUMN name_tr         VARCHAR(120);

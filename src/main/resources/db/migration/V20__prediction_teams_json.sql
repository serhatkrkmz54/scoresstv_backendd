-- Prediction yanitindaki "teams" objesini ham JSON olarak sakla.
-- API yapisi cok zengin (last_5, league form/fixtures/goals/cards/lineups,
-- biggest, clean_sheet, ...) — 80+ alani ayri kolon yapmak mantiksiz; JSONB
-- ile esnek + sorgu disinda tutariz. Frontend dogrudan kullanir.

ALTER TABLE predictions
    ADD COLUMN IF NOT EXISTS teams_json JSONB;

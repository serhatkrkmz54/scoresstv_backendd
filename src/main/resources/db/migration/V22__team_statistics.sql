-- Bir takimin bir lig+sezondaki istatistikleri. API:
--   GET /teams/statistics?team=X&league=Y&season=Z
-- Yanit cok zengin (form, fixtures, goals/minute/under_over, biggest,
-- clean_sheet, failed_to_score, penalty, lineups, cards) — predictions
-- icindeki teams alanina benzer yapi. JSONB ile passthrough yapariz;
-- frontend dogrudan kullanir, ileride alan eklenince migration gerekmez.

CREATE TABLE IF NOT EXISTS team_statistics (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT      NOT NULL REFERENCES teams(id)   ON DELETE CASCADE,
    league_id   BIGINT      NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    season      INTEGER     NOT NULL,
    -- Ham {form, fixtures, goals, biggest, clean_sheet, failed_to_score,
    -- penalty, lineups, cards} objesi. Predictions teams_json'da yaptigimiz
    -- gibi JSONB.
    stats_json  JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_team_statistics_team_league_season
        UNIQUE (team_id, league_id, season)
);

-- Tipik sorgu: bir takimin guncel sezon oynadigi liglerin istatistikleri.
CREATE INDEX IF NOT EXISTS idx_team_statistics_team_season
    ON team_statistics (team_id, season);

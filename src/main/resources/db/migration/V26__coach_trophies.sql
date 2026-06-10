-- Tekik direktorun kazandigi kupalar. API:
--   GET /trophies?coach=X
-- Yanit: flat liste {league, country, season, place}.
-- Takim sayfasi mevcut kocun kupalarini gosterir (oyuncularinki ayri konu).

CREATE TABLE IF NOT EXISTS coach_trophies (
    id          BIGSERIAL PRIMARY KEY,
    coach_id    BIGINT       NOT NULL REFERENCES coaches(id) ON DELETE CASCADE,
    league      VARCHAR(255) NOT NULL,
    country     VARCHAR(100),
    season      VARCHAR(50),                       -- API "2018/2019" string
    place       VARCHAR(50),                       -- "Winner" / "2nd Place" / ...
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Idempotency: ayni kupa iki kez yazilmasin (resync sonrasi)
    CONSTRAINT uq_coach_trophies_unique
        UNIQUE (coach_id, league, season, place)
);

CREATE INDEX IF NOT EXISTS idx_coach_trophies_coach
    ON coach_trophies (coach_id);

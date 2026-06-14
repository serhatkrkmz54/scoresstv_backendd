-- =========================================================================
-- Basketbol takim detay sayfasi semasi.
--
-- 1) basketball_teams tablosunu profil + venue + iz alanlariyla genisletir.
-- 2) basketball_team_season_stats tablosunu olusturur — /teams/statistics
--    yanit kayitlarinin gunluk tazelenmis kopyasi (W/L, ortalamalar, en uzun
--    seriler, ev/deplasman breakdown JSONB).
--
-- Hizalandigi pattern: futboldaki TeamProfile + TeamSeasonStat tablolari.
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. basketball_teams genisletme
-- -------------------------------------------------------------------------
ALTER TABLE basketball_teams
    ADD COLUMN country_name             VARCHAR(120),
    ADD COLUMN country_code              VARCHAR(8),
    ADD COLUMN country_flag              VARCHAR(400),
    ADD COLUMN code                      VARCHAR(16),
    ADD COLUMN national                  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN founded                   INT,
    ADD COLUMN venue_name                VARCHAR(200),
    ADD COLUMN venue_city                VARCHAR(120),
    ADD COLUMN venue_capacity            INT,
    ADD COLUMN slug                      VARCHAR(180),
    ADD COLUMN covered                   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN last_profile_synced_at    TIMESTAMPTZ,
    ADD COLUMN last_stats_synced_at      TIMESTAMPTZ;

-- Slug arama (URL extract). Slug benzersiz olabilir ama id-suffix garantisi
-- nedeniyle UNIQUE constraint vermiyoruz — index yeterli.
CREATE INDEX IF NOT EXISTS idx_basketball_teams_slug
    ON basketball_teams (slug);

CREATE INDEX IF NOT EXISTS idx_basketball_teams_country
    ON basketball_teams (country_name);

CREATE INDEX IF NOT EXISTS idx_basketball_teams_covered
    ON basketball_teams (covered) WHERE covered = TRUE;

-- -------------------------------------------------------------------------
-- 2. basketball_team_season_stats — sezonluk takim metrikleri
--
-- API-Basketball /teams/statistics?team=X&league=Y&season=Z yanitinin
-- ozet kayit haline gelmis hali. Detayli ev/deplasman breakdown JSONB'da.
-- -------------------------------------------------------------------------
CREATE TABLE basketball_team_season_stats (
    id                      BIGSERIAL PRIMARY KEY,
    team_id                 BIGINT      NOT NULL REFERENCES basketball_teams(id),
    league_id               BIGINT      NOT NULL REFERENCES basketball_leagues(id),
    season                  VARCHAR(20) NOT NULL,

    -- Toplam (all) bloku
    games_played            INT,
    wins                    INT,
    loses                   INT,
    win_percentage          NUMERIC(6,3),

    -- Skor ortalamalari
    points_for_total        INT,
    points_for_avg          NUMERIC(8,2),
    points_against_total    INT,
    points_against_avg      NUMERIC(8,2),

    -- Seri
    longest_win_streak      INT,
    longest_lose_streak     INT,

    -- Form (son maclar, "WWLW" gibi)
    form                    VARCHAR(40),

    -- Ev/Deplasman breakdown (home: {played, wins, loses, points_for, points_against}, away: {...})
    home_away_json          JSONB,

    last_synced_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_bk_team_season_stats UNIQUE (team_id, league_id, season)
);

CREATE INDEX IF NOT EXISTS idx_bk_team_season_stats_team
    ON basketball_team_season_stats (team_id);

CREATE INDEX IF NOT EXISTS idx_bk_team_season_stats_league_season
    ON basketball_team_season_stats (league_id, season);

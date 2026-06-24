-- ============================================================
-- Voleybol puan durumu (standings) + takim sezon istatistikleri.
--   /standings?league=X&season=Y       -> volleyball_standings
--   /teams/statistics?team=&league=&season= -> volleyball_team_season_stats
--
-- Voleybol farki: beraberlik (draw) YOK. goals.for/against = toplam set sayisi.
-- ============================================================

CREATE TABLE volleyball_standings (
    id                  BIGSERIAL PRIMARY KEY,
    league_id           BIGINT NOT NULL REFERENCES volleyball_leagues(id),
    season              VARCHAR(20) NOT NULL,
    team_id             BIGINT NOT NULL REFERENCES volleyball_teams(id),
    stage               VARCHAR(80),
    group_name          VARCHAR(80) NOT NULL DEFAULT '',
    position            INT,
    games_played        INT,
    won                 INT,
    lost                INT,
    won_percentage      VARCHAR(10),
    lost_percentage     VARCHAR(10),
    -- Set farki (goals.for / goals.against = toplam set)
    sets_for            INT,
    sets_against        INT,
    points              INT,
    form                VARCHAR(20),
    description         VARCHAR(200),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_vb_standings UNIQUE (league_id, season, team_id, group_name)
);
CREATE INDEX idx_vb_standings_league_season ON volleyball_standings (league_id, season);
CREATE INDEX idx_vb_standings_team ON volleyball_standings (team_id);

CREATE TABLE volleyball_team_season_stats (
    id                      BIGSERIAL PRIMARY KEY,
    team_id                 BIGINT      NOT NULL REFERENCES volleyball_teams(id),
    league_id               BIGINT      NOT NULL REFERENCES volleyball_leagues(id),
    season                  VARCHAR(20) NOT NULL,

    -- Toplam (all) bloku
    games_played            INT,
    wins                    INT,
    loses                   INT,
    win_percentage          NUMERIC(6,3),

    -- Set ortalamalari (goals = set/sayi)
    sets_for_total          INT,
    sets_for_avg            NUMERIC(8,2),
    sets_against_total      INT,
    sets_against_avg        NUMERIC(8,2),

    -- Form (son maclar, "WWLW" gibi)
    form                    VARCHAR(40),

    -- Ev/Deplasman breakdown
    home_away_json          JSONB,

    last_synced_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_vb_team_season_stats UNIQUE (team_id, league_id, season)
);
CREATE INDEX idx_vb_team_season_stats_team
    ON volleyball_team_season_stats (team_id);
CREATE INDEX idx_vb_team_season_stats_league_season
    ON volleyball_team_season_stats (league_id, season);

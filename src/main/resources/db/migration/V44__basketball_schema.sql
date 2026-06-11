-- ============================================================
-- Basketbol (API-Basketball v1) şeması — football'dan TAMAMEN ayrı tablolar.
-- Faz 1: canlı skor + fikstür (games). Standings/detay sonraki fazlarda.
-- ============================================================

CREATE TABLE basketball_leagues (
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    type          VARCHAR(40),
    logo          VARCHAR(400),
    country_name  VARCHAR(120),
    country_code  VARCHAR(10),
    country_flag  VARCHAR(400),
    updated_at    TIMESTAMPTZ
);

CREATE TABLE basketball_teams (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    logo        VARCHAR(400),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE basketball_games (
    id              BIGINT PRIMARY KEY,
    league_id       BIGINT NOT NULL REFERENCES basketball_leagues(id),
    season          VARCHAR(20),
    home_team_id    BIGINT NOT NULL REFERENCES basketball_teams(id),
    away_team_id    BIGINT NOT NULL REFERENCES basketball_teams(id),
    start_at        TIMESTAMPTZ NOT NULL,
    status_short    VARCHAR(10),
    status_long     VARCHAR(60),
    timer           VARCHAR(20),
    stage           VARCHAR(60),
    week            VARCHAR(60),
    home_q1         INT,
    home_q2         INT,
    home_q3         INT,
    home_q4         INT,
    home_ot         INT,
    home_total      INT,
    away_q1         INT,
    away_q2         INT,
    away_q3         INT,
    away_q4         INT,
    away_ot         INT,
    away_total      INT,
    last_synced_at  TIMESTAMPTZ
);

CREATE INDEX idx_bk_games_start_at ON basketball_games (start_at);
CREATE INDEX idx_bk_games_status   ON basketball_games (status_short);
CREATE INDEX idx_bk_games_league   ON basketball_games (league_id);

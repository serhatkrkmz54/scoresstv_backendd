-- ============================================================
-- Voleybol (API-Volleyball v1) semasi — football/basketball'dan TAMAMEN ayri
-- tablolar. Referans (ulke/lig/takim) + fikstur (games) + junction.
--
-- VOLEYBOL SKOR MODELI (basketboldan FARKLI):
--   scores.home/away = KAZANILAN SET sayisi (home_total/away_total, 0..3)
--   periods.first..fifth = her setteki SAYI (home_set1..5 / away_set1..5)
--   ceyrek/overtime YOK, timer/clock YOK (set bazli).
-- ============================================================

CREATE TABLE volleyball_countries (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    name_tr     VARCHAR(120),
    code        VARCHAR(10),
    flag        VARCHAR(400),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE volleyball_leagues (
    id                BIGINT PRIMARY KEY,
    name              VARCHAR(160) NOT NULL,
    name_tr           VARCHAR(160),
    type              VARCHAR(40),
    logo              VARCHAR(400),
    logo_key          VARCHAR(255),
    country_name      VARCHAR(120),
    country_name_tr   VARCHAR(120),
    country_code      VARCHAR(10),
    country_flag      VARCHAR(400),
    country_flag_key  VARCHAR(255),
    current_season    VARCHAR(20),
    slug              VARCHAR(180),
    seasons_json      JSONB,
    covered           BOOLEAN NOT NULL DEFAULT FALSE,
    last_info_synced_at TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ
);
CREATE INDEX idx_vb_leagues_slug ON volleyball_leagues (slug);
CREATE INDEX idx_vb_leagues_covered ON volleyball_leagues (covered) WHERE covered = TRUE;

CREATE TABLE volleyball_teams (
    id                      BIGINT PRIMARY KEY,
    name                    VARCHAR(160) NOT NULL,
    name_tr                 VARCHAR(160),
    logo                    VARCHAR(400),
    logo_key                VARCHAR(255),
    country_name            VARCHAR(120),
    country_code            VARCHAR(8),
    country_flag            VARCHAR(400),
    national                BOOLEAN NOT NULL DEFAULT FALSE,
    slug                    VARCHAR(180),
    covered                 BOOLEAN NOT NULL DEFAULT FALSE,
    last_profile_synced_at  TIMESTAMPTZ,
    last_stats_synced_at    TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ
);
CREATE INDEX idx_vb_teams_slug ON volleyball_teams (slug);
CREATE INDEX idx_vb_teams_country ON volleyball_teams (country_name);

CREATE TABLE volleyball_games (
    id              BIGINT PRIMARY KEY,
    league_id       BIGINT NOT NULL REFERENCES volleyball_leagues(id),
    season          VARCHAR(20),
    home_team_id    BIGINT NOT NULL REFERENCES volleyball_teams(id),
    away_team_id    BIGINT NOT NULL REFERENCES volleyball_teams(id),
    start_at        TIMESTAMPTZ NOT NULL,
    status_short    VARCHAR(10),
    status_long     VARCHAR(60),
    stage           VARCHAR(60),
    week            VARCHAR(60),
    -- Kazanilan set sayisi (0..3)
    home_total      INT,
    away_total      INT,
    -- Set bazli sayilar (her set icin home/away, nullable)
    home_set1       INT,
    home_set2       INT,
    home_set3       INT,
    home_set4       INT,
    home_set5       INT,
    away_set1       INT,
    away_set2       INT,
    away_set3       INT,
    away_set4       INT,
    away_set5       INT,
    -- Push idempotency durum kolonlari
    notified_start        BOOLEAN NOT NULL DEFAULT FALSE,
    notified_final        BOOLEAN NOT NULL DEFAULT FALSE,
    last_notified_period  INTEGER NOT NULL DEFAULT 0,
    last_synced_at  TIMESTAMPTZ
);
CREATE INDEX idx_vb_games_start_at ON volleyball_games (start_at);
CREATE INDEX idx_vb_games_status   ON volleyball_games (status_short);
CREATE INDEX idx_vb_games_league   ON volleyball_games (league_id);

-- Lig+sezon coverage / sync zamanlari (standings DailyJob icin)
CREATE TABLE volleyball_seasons (
    id                       BIGSERIAL PRIMARY KEY,
    league_id                BIGINT NOT NULL REFERENCES volleyball_leagues(id),
    season                   VARCHAR(20) NOT NULL,
    coverage_standings       BOOLEAN NOT NULL DEFAULT TRUE,
    standings_last_synced_at TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT uk_vb_seasons UNIQUE (league_id, season)
);
CREATE INDEX idx_vb_seasons_league ON volleyball_seasons (league_id);

-- Takim ↔ lig+sezon kalici uyelik junction (onboarding favori takim icin).
CREATE TABLE volleyball_team_league_seasons (
    team_id   BIGINT NOT NULL REFERENCES volleyball_teams(id) ON DELETE CASCADE,
    league_id BIGINT NOT NULL REFERENCES volleyball_leagues(id) ON DELETE CASCADE,
    season    VARCHAR(20) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, league_id, season)
);
CREATE INDEX idx_volleyball_tls_league_season
    ON volleyball_team_league_seasons (league_id, season);
CREATE INDEX idx_volleyball_tls_team_season
    ON volleyball_team_league_seasons (team_id, season);

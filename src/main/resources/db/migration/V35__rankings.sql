-- V35 — FIFA + UEFA siralamalari icin 3 tablo.
--
-- 1. fifa_rankings — FIFA Erkek Milli Takim Siralamasi (211 ulke).
--    Kaynak: https://api.fifa.com/api/v3/fifarankings/rankings/live
--    Her sync REPLACE (tum satirlar silinip yeniden yazilir).
--
-- 2. uefa_club_rankings — UEFA Kulup Katsayisi (~415 kulup).
--    Kaynak: https://comp.uefa.com/v2/coefficients?coefficientType=MEN_CLUB
--    Her sync REPLACE.
--
-- 3. uefa_country_rankings — UEFA Milli Takim Katsayisi (55 ulke).
--    Kaynak: https://comp.uefa.com/v2/coefficients?coefficientType=MEN_ASSOCIATION
--    Her sync REPLACE.
--
-- Hepsi gunde 1 kez tazelenir (DailyRankingsJob, 03:00).

-- ============================================================
-- FIFA Erkek Milli Takim Siralamasi
-- ============================================================

CREATE TABLE fifa_rankings (
    id BIGSERIAL PRIMARY KEY,
    rank INT NOT NULL,
    prev_rank INT,
    movement INT,
    team_id VARCHAR(50) NOT NULL,
    team_name VARCHAR(120) NOT NULL,
    country_code VARCHAR(10) NOT NULL,
    confederation VARCHAR(20),
    confederation_id VARCHAR(50),
    total_points NUMERIC(10, 6) NOT NULL,
    prev_points NUMERIC(10, 6),
    rated_matches INT,
    last_synced_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fifa_rankings_team_id UNIQUE (team_id)
);

CREATE INDEX idx_fifa_rankings_rank ON fifa_rankings (rank);
CREATE INDEX idx_fifa_rankings_confederation ON fifa_rankings (confederation);
CREATE INDEX idx_fifa_rankings_country_code ON fifa_rankings (country_code);

COMMENT ON TABLE fifa_rankings IS 'FIFA Erkek Milli Takim Siralamasi — gunluk tazelenir';
COMMENT ON COLUMN fifa_rankings.movement IS 'Pozitif = ust siralara cikti, negatif = dustu';

-- ============================================================
-- UEFA Kulup Katsayisi
-- ============================================================

CREATE TABLE uefa_club_rankings (
    id BIGSERIAL PRIMARY KEY,
    club_id VARCHAR(50) NOT NULL,
    club_name VARCHAR(120) NOT NULL,
    club_short_name VARCHAR(80),
    club_official_name VARCHAR(160),
    team_code VARCHAR(10),
    logo_url TEXT,
    big_logo_url TEXT,
    medium_logo_url TEXT,
    country_code VARCHAR(10) NOT NULL,
    country_name VARCHAR(80),
    association_id BIGINT,
    rank INT NOT NULL,
    total_points NUMERIC(10, 3) NOT NULL,
    trend VARCHAR(10),
    number_of_matches INT,
    number_of_teams INT,
    target_season_year INT NOT NULL,
    base_season_year INT,
    season_rankings_json JSONB,
    last_synced_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_uefa_club_rankings_club_season UNIQUE (club_id, target_season_year)
);

CREATE INDEX idx_uefa_club_rankings_rank ON uefa_club_rankings (rank);
CREATE INDEX idx_uefa_club_rankings_country_code ON uefa_club_rankings (country_code);
CREATE INDEX idx_uefa_club_rankings_target_season ON uefa_club_rankings (target_season_year);

COMMENT ON TABLE uefa_club_rankings IS 'UEFA Kulup Katsayisi (5 sezon toplami) — gunluk tazelenir';
COMMENT ON COLUMN uefa_club_rankings.trend IS 'UP / DOWN / STABLE';
COMMENT ON COLUMN uefa_club_rankings.season_rankings_json IS 'Son 5 sezonun ayri puanlari (JSON dizi)';

-- ============================================================
-- UEFA Milli Takim Katsayisi
-- ============================================================

CREATE TABLE uefa_country_rankings (
    id BIGSERIAL PRIMARY KEY,
    country_uefa_id VARCHAR(50) NOT NULL,
    country_name VARCHAR(80) NOT NULL,
    country_code VARCHAR(10) NOT NULL,
    logo_url TEXT,
    big_logo_url TEXT,
    medium_logo_url TEXT,
    association_id BIGINT,
    rank INT NOT NULL,
    total_points NUMERIC(10, 3) NOT NULL,
    trend VARCHAR(10),
    number_of_matches INT,
    number_of_teams INT,
    target_season_year INT NOT NULL,
    season_rankings_json JSONB,
    last_synced_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_uefa_country_rankings_country_season UNIQUE (country_uefa_id, target_season_year)
);

CREATE INDEX idx_uefa_country_rankings_rank ON uefa_country_rankings (rank);
CREATE INDEX idx_uefa_country_rankings_country_code ON uefa_country_rankings (country_code);
CREATE INDEX idx_uefa_country_rankings_target_season ON uefa_country_rankings (target_season_year);

COMMENT ON TABLE uefa_country_rankings IS 'UEFA Milli Takim Katsayisi (5 sezon toplami) — gunluk tazelenir';

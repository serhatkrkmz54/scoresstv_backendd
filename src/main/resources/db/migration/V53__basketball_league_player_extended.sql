-- =================================================================
-- V53: Basketbol lig sayfasi + player master tablo genisletme
-- =================================================================
--
-- Bu migration basketbol icin futboldaki "lig detay sayfasi" + "oyuncu
-- profil" altyapisinin DB tarafini hazirlar:
--
--   1) basketball_leagues genisletme: slug, seasons JSONB, covered flag,
--      sync timestamp'leri (lazy sync freshness icin).
--
--   2) basketball_players genisletme: slug, first/last name, foto + foto
--      MinIO key, ulke, dogum tarihi, boy/kilo, forma no, pozisyon.
--      Mevcut iskelet (sadece name + team_id) korunur, yeni kolonlar
--      nullable eklenir.
--
--   3) Yeni tablo: basketball_player_season_stats — bir oyuncunun belirli
--      lig + sezondaki ortalama istatistikleri. API-Basketball /players
--      endpoint'inden gelir, oyuncu profil ekraninda gosterilir, ayrica
--      top players sirasi icin sort kaynagi.
--
--   4) Yeni tablo: basketball_league_top_players — lig + sezon basina
--      3 kategori (SCORERS/REBOUNDERS/ASSISTS) icin top 10 sirasi.
--      Futboldaki league_top_players patterni; replace stratejisiyle
--      tazelenir.
--
-- Tum yeni kolonlar nullable veya default'lu — mevcut basketbol verisi
-- bozulmaz.

-- ------------------------------------------------------------
-- 1) basketball_leagues genisletme
-- ------------------------------------------------------------
ALTER TABLE basketball_leagues
    ADD COLUMN slug                         VARCHAR(180),
    ADD COLUMN seasons_json                 JSONB,
    ADD COLUMN covered                      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN last_info_synced_at          TIMESTAMPTZ,
    ADD COLUMN last_top_players_synced_at   TIMESTAMPTZ;

-- slug uzerinde unique index (URL'den ID cozumlemesi icin). nullable kalir
-- ki backfill gradual yapilabilsin; dolu olanlar uzerinde benzersizlik.
CREATE UNIQUE INDEX IF NOT EXISTS uk_bk_leagues_slug
    ON basketball_leagues (slug) WHERE slug IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bk_leagues_covered
    ON basketball_leagues (covered) WHERE covered = TRUE;

-- ------------------------------------------------------------
-- 2) basketball_players genisletme
-- ------------------------------------------------------------
ALTER TABLE basketball_players
    ADD COLUMN slug                       VARCHAR(180),
    ADD COLUMN first_name                 VARCHAR(120),
    ADD COLUMN last_name                  VARCHAR(120),
    ADD COLUMN photo                      VARCHAR(400),
    ADD COLUMN photo_key                  VARCHAR(255),
    ADD COLUMN birth_date                 DATE,
    ADD COLUMN birth_place                VARCHAR(160),
    ADD COLUMN birth_country              VARCHAR(120),
    ADD COLUMN nationality                VARCHAR(120),
    ADD COLUMN height_cm                  INT,
    ADD COLUMN weight_kg                  INT,
    ADD COLUMN jersey_number              INT,
    ADD COLUMN position                   VARCHAR(40),
    ADD COLUMN college                    VARCHAR(200),
    ADD COLUMN last_profile_synced_at     TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bk_players_slug
    ON basketball_players (slug) WHERE slug IS NOT NULL;

-- ------------------------------------------------------------
-- 3) basketball_player_season_stats — sezonluk ortalama istatistikler
-- ------------------------------------------------------------
-- Bir oyuncu, bir lig + sezon icin tek satir. NBA gibi "oyuncu transfer
-- olduysa" durumlari icin team_id geirim sirasinda guncellenir (sezon
-- sonu takim). Sayilar API'den NUMERIC (ondalik) gelir (PPG = points
-- per game).
CREATE TABLE basketball_player_season_stats (
    id                BIGSERIAL PRIMARY KEY,
    player_id         BIGINT       NOT NULL REFERENCES basketball_players(id) ON DELETE CASCADE,
    league_id         BIGINT       NOT NULL REFERENCES basketball_leagues(id),
    season            VARCHAR(20)  NOT NULL,
    team_id           BIGINT REFERENCES basketball_teams(id),
    -- Maç bazli
    games_played      INT,
    games_started     INT,
    minutes_per_game  NUMERIC(5,2),
    -- Skorlama
    points_per_game   NUMERIC(5,2),
    field_goals_made     NUMERIC(5,2),
    field_goals_attempts NUMERIC(5,2),
    field_goals_pct      NUMERIC(5,2),
    threepoint_made      NUMERIC(5,2),
    threepoint_attempts  NUMERIC(5,2),
    threepoint_pct       NUMERIC(5,2),
    freethrows_made      NUMERIC(5,2),
    freethrows_attempts  NUMERIC(5,2),
    freethrows_pct       NUMERIC(5,2),
    -- Ribaund
    rebounds_total       NUMERIC(5,2),
    rebounds_offence     NUMERIC(5,2),
    rebounds_defense     NUMERIC(5,2),
    -- Diger
    assists_per_game     NUMERIC(5,2),
    steals_per_game      NUMERIC(5,2),
    blocks_per_game      NUMERIC(5,2),
    turnovers_per_game   NUMERIC(5,2),
    fouls_per_game       NUMERIC(5,2),
    -- Audit
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bk_player_season UNIQUE (player_id, league_id, season)
);

CREATE INDEX idx_bk_pss_league_season
    ON basketball_player_season_stats (league_id, season);
CREATE INDEX idx_bk_pss_player
    ON basketball_player_season_stats (player_id);

-- ------------------------------------------------------------
-- 4) basketball_league_top_players — top scorers / rebounders / assists
-- ------------------------------------------------------------
-- 3 kategori × 10 sira = lig + sezon basina 30 satir. Replace stratejisi:
-- sezon basi sync icin (lig_id, sezon, kategori) silinir, yeniden insert
-- edilir. Unique key (lig+sezon+kategori+sira) ile cakisma onlenir.
CREATE TABLE basketball_league_top_players (
    id            BIGSERIAL PRIMARY KEY,
    league_id     BIGINT       NOT NULL REFERENCES basketball_leagues(id) ON DELETE CASCADE,
    season        VARCHAR(20)  NOT NULL,
    category      VARCHAR(20)  NOT NULL,   -- SCORERS | REBOUNDERS | ASSISTS
    position      INT          NOT NULL,   -- 1-10 sira
    player_id     BIGINT       NOT NULL REFERENCES basketball_players(id),
    team_id       BIGINT REFERENCES basketball_teams(id),
    value         NUMERIC(6,2),            -- siralama metrigi (PPG/RPG/APG)
    games_played  INT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_bk_top_players UNIQUE (league_id, season, category, position),
    CONSTRAINT ck_bk_top_category
        CHECK (category IN ('SCORERS', 'REBOUNDERS', 'ASSISTS'))
);

CREATE INDEX idx_bk_top_players_league_season
    ON basketball_league_top_players (league_id, season);
CREATE INDEX idx_bk_top_players_player
    ON basketball_league_top_players (player_id);

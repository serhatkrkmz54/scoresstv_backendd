-- ============================================================
-- B-Faz1: Basketbol maç detayı + puan durumu + oyuncu altyapısı.
-- API-Basketball v1 ucları:
--   /games/statistics/teams?id=X    -> basketball_game_team_stats
--   /games/statistics/players?id=X  -> basketball_game_player_stats (+ players master)
--   /standings?league=X&season=Y    -> basketball_standings
--   /games/h2h?h2h=A-B              -> mevcut basketball_games tablosundan okur
-- ============================================================

-- ------------------------------------------------------------
-- 1) Oyuncu master tablosu — game_player_stats FK hedefi.
--    Suanki takim referansi opsiyonel; player sayfasi sonraki faz icin.
-- ------------------------------------------------------------
CREATE TABLE basketball_players (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    team_id     BIGINT REFERENCES basketball_teams(id),
    updated_at  TIMESTAMPTZ
);
CREATE INDEX idx_bk_players_team ON basketball_players (team_id);

-- ------------------------------------------------------------
-- 2) Puan durumu (standings).
--    NBA gibi liglerde "Eastern Conference" / "Western Conference"
--    grup ayrimi var. EuroLeague Final Four icin "Group A" gibi.
--    Unique key'e group_name dahil; futbol standings paterni.
-- ------------------------------------------------------------
CREATE TABLE basketball_standings (
    id                  BIGSERIAL PRIMARY KEY,
    league_id           BIGINT NOT NULL REFERENCES basketball_leagues(id),
    season              VARCHAR(20) NOT NULL,
    team_id             BIGINT NOT NULL REFERENCES basketball_teams(id),
    stage               VARCHAR(80),
    group_name          VARCHAR(80) NOT NULL DEFAULT '',
    position            INT,
    -- Galibiyetler
    won_all             INT,
    won_home            INT,
    won_away            INT,
    won_percentage      VARCHAR(10),
    -- Maglubiyetler
    lost_all            INT,
    lost_home           INT,
    lost_away           INT,
    lost_percentage     VARCHAR(10),
    -- Oynanan mac sayilari
    games_played_all    INT,
    games_played_home   INT,
    games_played_away   INT,
    -- Sayi farki (points for/against)
    points_for          INT,
    points_against      INT,
    form                VARCHAR(20),
    description         VARCHAR(200),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_bk_standings UNIQUE (league_id, season, team_id, group_name)
);
CREATE INDEX idx_bk_standings_league_season ON basketball_standings (league_id, season);
CREATE INDEX idx_bk_standings_team ON basketball_standings (team_id);

-- ------------------------------------------------------------
-- 3) Mac basina TAKIM istatistikleri — 2 satir per game.
--    Replace strategy: sync sirasinda game_id'ye gore eski satirlar
--    silinip yenisi insert edilir (upserter).
-- ------------------------------------------------------------
CREATE TABLE basketball_game_team_stats (
    id                  BIGSERIAL PRIMARY KEY,
    game_id             BIGINT NOT NULL REFERENCES basketball_games(id) ON DELETE CASCADE,
    team_id             BIGINT NOT NULL REFERENCES basketball_teams(id),
    -- 2pt + 3pt birlesik field goals
    fg_total            INT,
    fg_attempts         INT,
    fg_percentage       VARCHAR(10),
    -- Sadece 3pt
    tp_total            INT,
    tp_attempts         INT,
    tp_percentage       VARCHAR(10),
    -- Free throws
    ft_total            INT,
    ft_attempts         INT,
    ft_percentage       VARCHAR(10),
    -- Ribauntlar
    rebounds_total      INT,
    rebounds_offence    INT,
    rebounds_defense    INT,
    assists             INT,
    steals              INT,
    blocks              INT,
    turnovers           INT,
    personal_fouls      INT,
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_bk_game_team_stats UNIQUE (game_id, team_id)
);
CREATE INDEX idx_bk_gts_game ON basketball_game_team_stats (game_id);
CREATE INDEX idx_bk_gts_team ON basketball_game_team_stats (team_id);

-- ------------------------------------------------------------
-- 4) Mac basina OYUNCU istatistikleri — game basina N satir.
--    "type" alani: "starters" veya "bench" (API'den geliyor).
--    player_name kolonu defansif: player master eksikse de gosterilebilsin.
-- ------------------------------------------------------------
CREATE TABLE basketball_game_player_stats (
    id                  BIGSERIAL PRIMARY KEY,
    game_id             BIGINT NOT NULL REFERENCES basketball_games(id) ON DELETE CASCADE,
    team_id             BIGINT NOT NULL REFERENCES basketball_teams(id),
    player_id           BIGINT NOT NULL REFERENCES basketball_players(id),
    player_name         VARCHAR(160),
    type                VARCHAR(20),
    minutes             VARCHAR(10),
    fg_total            INT,
    fg_attempts         INT,
    fg_percentage       VARCHAR(10),
    tp_total            INT,
    tp_attempts         INT,
    tp_percentage       VARCHAR(10),
    ft_total            INT,
    ft_attempts         INT,
    ft_percentage       VARCHAR(10),
    rebounds_total      INT,
    rebounds_offence    INT,
    rebounds_defense    INT,
    assists             INT,
    points              INT,
    steals              INT,
    blocks              INT,
    turnovers           INT,
    personal_fouls      INT,
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_bk_game_player_stats UNIQUE (game_id, player_id)
);
CREATE INDEX idx_bk_gps_game   ON basketball_game_player_stats (game_id);
CREATE INDEX idx_bk_gps_player ON basketball_game_player_stats (player_id);
CREATE INDEX idx_bk_gps_team   ON basketball_game_player_stats (team_id);

-- ------------------------------------------------------------
-- 5) Sezon coverage / sync zamanlari — standings DailyJob icin.
--    Futbol "seasons" tablosunun basit basketbol esi.
-- ------------------------------------------------------------
CREATE TABLE basketball_seasons (
    id                       BIGSERIAL PRIMARY KEY,
    league_id                BIGINT NOT NULL REFERENCES basketball_leagues(id),
    season                   VARCHAR(20) NOT NULL,
    coverage_standings       BOOLEAN NOT NULL DEFAULT TRUE,
    standings_last_synced_at TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ,
    CONSTRAINT uk_bk_seasons UNIQUE (league_id, season)
);
CREATE INDEX idx_bk_seasons_league ON basketball_seasons (league_id);

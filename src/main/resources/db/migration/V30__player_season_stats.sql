-- Oyuncu sezonluk istatistikleri (aggregated). API:
--   GET /players?team=X&season=Y       (sayfali, takim kadrosu icin tum oyuncular)
--   GET /players?id=P&season=Y         (tek oyuncu, tum liglerdeki sezon istatistikleri)
--
-- Yanit yapisi: { player: {...}, statistics: [ {team, league, games, goals,
-- shots, passes, tackles, duels, dribbles, fouls, cards, penalty, substitutes},
-- ... ] }  — bir oyuncu ayni sezonda birden fazla turnuvada (lig + kupa + CL)
-- oynayabilir; statistics[] her turnuva icin ayri obje doner.
--
-- Bizim DB modeli: her statistics[] elementi tek bir satir (player, team,
-- league, season) anahtariyla. {@code stats_json} JSONB passthrough — frontend
-- gunlukluler (gol/asist/dakika/rating/pas/sut/tackle/...) hangi alani isterse
-- icinden alir. team_statistics ile ayni desen.
CREATE TABLE IF NOT EXISTS player_season_stats (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT      NOT NULL,
    team_id     BIGINT      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    league_id   BIGINT      NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    season      INTEGER     NOT NULL,
    stats_json  JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_player_season_stats UNIQUE (player_id, team_id, league_id, season)
);

-- Takim sayfasi okuma: bir takimin tum oyuncularinin belirli sezondaki
-- istatistikleri. UI sezon dropdown'una gore tetiklenir.
CREATE INDEX IF NOT EXISTS idx_player_season_stats_team_season
    ON player_season_stats (team_id, season);

-- Oyuncu sayfasi okuma (gelecekteki): bir oyuncunun tum kariyer istatistikleri.
CREATE INDEX IF NOT EXISTS idx_player_season_stats_player
    ON player_season_stats (player_id, season DESC);

-- Bir takimin sezon icindeki kadrosu. API:
--   GET /players/squads?team=X
-- Yanit guncel kadroyu doner; eski sezonlari icin /players?team=X&season=Y
-- (sayfalama gerektirir).
-- Her oyuncu icin position, jersey number, ya$, foto bilgisi var.
-- Player master tablo (V19) ile player_id referans verir; oyuncu adi ve
-- foto orada master kaydedilir. Bu tabloda sezon-bazli denormalize
-- snapshot (jersey numarasi sezonluk degisir).

CREATE TABLE IF NOT EXISTS team_squad (
    id              BIGSERIAL PRIMARY KEY,
    team_id         BIGINT      NOT NULL REFERENCES teams(id)   ON DELETE CASCADE,
    season          INTEGER     NOT NULL,
    player_id       BIGINT      NOT NULL,   -- Player master ile referans
    position        VARCHAR(20),             -- "Goalkeeper" / "Defender" / "Midfielder" / "Attacker"
    jersey_number   INTEGER,
    -- Snapshot: oyuncu adi (master ile karsilik gelir; freshness yardimi)
    player_name     VARCHAR(255) NOT NULL,
    -- Snapshot: yas (sezon basi). Master tablo guncel yas tutar; bu sezona
    -- ait yas burada saklanir.
    player_age      INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_team_squad_team_season_player
        UNIQUE (team_id, season, player_id)
);

-- Tipik sorgu: bir takimin guncel sezon kadrosunu pozisyon sirali al.
CREATE INDEX IF NOT EXISTS idx_team_squad_team_season
    ON team_squad (team_id, season);

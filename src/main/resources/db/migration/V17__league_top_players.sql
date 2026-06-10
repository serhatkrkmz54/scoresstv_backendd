-- Bir lig+sezon icin top scorers / assists / cards listesi.
-- Tek tablo + category discriminator: 3 ayri tablo yerine sade tutuyoruz.
-- API endpoint'leri:
--   GET /players/topscorers?league=X&season=Y
--   GET /players/topassists?league=X&season=Y
--   GET /players/topcards?league=X&season=Y
-- Hepsi ayni "player + statistics" yapisini doner; biz sadece o kategorinin
-- onemli metriklerini cikarip flat sutunlara yaziyoruz.

CREATE TABLE IF NOT EXISTS league_top_players (
    id              BIGSERIAL PRIMARY KEY,

    league_id       BIGINT       NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    season          INTEGER      NOT NULL,
    -- 'SCORERS' | 'ASSISTS' | 'CARDS'
    category        VARCHAR(16)  NOT NULL,

    -- API sıralamasında oyuncunun yeri (1 = en yuksek). API zaten sirali
    -- doner; biz de bu siraya gore kaydediyoruz.
    rank            INTEGER      NOT NULL,

    -- Oyuncu (player) bilgileri
    player_id       BIGINT       NOT NULL,
    player_name     VARCHAR(255) NOT NULL,
    player_photo    VARCHAR(500),
    player_nationality VARCHAR(100),
    player_age      INTEGER,

    -- Takim bilgileri (oyuncunun bu sezon hangi takimda)
    team_id         BIGINT,
    team_name       VARCHAR(150),
    team_logo       VARCHAR(500),

    -- Kategoriye gore birincil deger:
    --   SCORERS -> goals.total
    --   ASSISTS -> goals.assists
    --   CARDS   -> cards.yellow (red ayri kolonda)
    value_primary   INTEGER,
    -- CARDS icin red kart sayisi (digerleri NULL)
    value_secondary INTEGER,

    -- Ek istatistikler (UI'da ikincil bilgi olarak)
    appearances     INTEGER,
    minutes         INTEGER,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_top_players_unique
        UNIQUE (league_id, season, category, player_id)
);

-- Sik sorgu: bir lig+sezon+kategorinin top N oyuncusu (rank sirali).
CREATE INDEX IF NOT EXISTS idx_top_players_league_season_cat_rank
    ON league_top_players (league_id, season, category, rank);

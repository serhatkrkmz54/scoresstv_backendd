-- Player detay sayfasi icin: master tabloyu genislet + kariyer takimlari +
-- kupalar tablosu. /players?id=X&season=Y, /players/teams?player=X,
-- /trophies?player=X endpoint'lerini destekler.

-- ============================================================
-- 1. players master tablosuna profil alanlari ekle
-- ============================================================
-- API /players?id=X yaniti player altinda firstname/lastname/age/nationality/
-- birth/height/weight/injured alanlarini doner. Bunlari master tabloya
-- ekleyip her sezon sync'inde tazeleyecegiz.
ALTER TABLE players
    ADD COLUMN IF NOT EXISTS firstname     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS lastname      VARCHAR(120),
    ADD COLUMN IF NOT EXISTS age           INTEGER,
    ADD COLUMN IF NOT EXISTS birth_date    DATE,
    ADD COLUMN IF NOT EXISTS birth_place   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS birth_country VARCHAR(100),
    ADD COLUMN IF NOT EXISTS nationality   VARCHAR(100),
    -- API "190 cm" / "84 kg" gibi string doner — ham sakliyoruz.
    ADD COLUMN IF NOT EXISTS height        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS weight        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS injured       BOOLEAN;

-- ============================================================
-- 2. player_career_teams: oyuncunun TUM kariyerindeki takimlar + sezonlar
-- ============================================================
-- API yaniti her takim icin seasons[] dizisi doner (oyuncunun o takimda
-- oynadigi yillar). Bunu JSONB ile passthrough sakliyoruz — UI dropdown'a
-- ham doner. Cogu zaman 5-15 takim/oyuncu (Sahin: 7 takim, Ronaldo: 6).
CREATE TABLE IF NOT EXISTS player_career_teams (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT      NOT NULL,
    team_id     BIGINT      NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    -- Sezon yillari array: [2025, 2024, 2023, 2022, 2021, 2020]
    seasons     JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_player_career_teams UNIQUE (player_id, team_id)
);

-- Player sayfasi okuma: bir oyuncunun TUM kariyer takimlari.
CREATE INDEX IF NOT EXISTS idx_player_career_teams_player
    ON player_career_teams (player_id);

-- ============================================================
-- 3. player_trophies: oyuncu kariyer kupalari
-- ============================================================
-- API /trophies?player=X yaniti coach version ile aynidir (ayni person id'de
-- her ikisi birlestirilmis doner). Bizim taban karari: ayri tablo (cleaner)
-- coach_trophies ile aynisi player_trophies, place ham + cevirili.
CREATE TABLE IF NOT EXISTS player_trophies (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT       NOT NULL,
    league      VARCHAR(255) NOT NULL,
    country     VARCHAR(100),
    season      VARCHAR(50),
    place       VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_player_trophies UNIQUE (player_id, league, season, place)
);

CREATE INDEX IF NOT EXISTS idx_player_trophies_player
    ON player_trophies (player_id);

-- ============================================================
-- 4. players.covered — DailyPlayerRefreshJob icin
-- ============================================================
ALTER TABLE players
    ADD COLUMN IF NOT EXISTS covered BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_players_covered
    ON players (covered) WHERE covered = TRUE;

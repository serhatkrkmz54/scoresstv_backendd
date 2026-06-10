-- Tekik direktor (coach) master tablo. API:
--   GET /coachs?team=X       (mevcut koc)
--   GET /coachs?id=X         (tek koc detayi)
-- Yanit: id, name, firstname, lastname, age, birth, nationality, height,
-- weight, photo, team (mevcut), career[] (gecmis tum takimlar).
--
-- coach_career: bir kocun gecmis ve mevcut takimlari. start/end tarih.
-- Bizim takim sayfasinda yalniz "mevcut koc" gosterilir; geçmiş koçlar
-- API'de team-bazli sorgulanamiyor. coach_career master kayit olarak
-- tutulur (oyuncu/koc detay sayfalarinda kariyer gosterimi).

CREATE TABLE IF NOT EXISTS coaches (
    id              BIGINT       PRIMARY KEY,           -- API-Football coach id
    name            VARCHAR(255) NOT NULL,
    firstname       VARCHAR(120),
    lastname        VARCHAR(120),
    age             INTEGER,
    birth_date      DATE,
    birth_place     VARCHAR(120),
    birth_country   VARCHAR(100),
    nationality     VARCHAR(100),
    height          VARCHAR(20),  -- "192 cm" - API string
    weight          VARCHAR(20),
    photo_url       VARCHAR(500),
    photo_key       VARCHAR(255), -- MinIO mirror sonrasi
    -- Mevcut takim (varsa). Geçmiş koc icin null olabilir.
    current_team_id BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_coaches_current_team
    ON coaches (current_team_id) WHERE current_team_id IS NOT NULL;

-- Aynalanmamis foto'lar icin: ImageMirrorService.mirrorCoachPhotos
CREATE INDEX IF NOT EXISTS idx_coaches_photo_key_null
    ON coaches (id) WHERE photo_key IS NULL AND photo_url IS NOT NULL;

-- Kocun kariyeri: gecmis ve mevcut takim donemleri.
CREATE TABLE IF NOT EXISTS coach_career (
    id          BIGSERIAL PRIMARY KEY,
    coach_id    BIGINT       NOT NULL REFERENCES coaches(id) ON DELETE CASCADE,
    team_id     BIGINT,                    -- Takim master ile referans (null olabilir)
    team_name   VARCHAR(150),              -- Snapshot
    team_logo   VARCHAR(500),              -- Snapshot
    start_date  DATE,
    end_date    DATE,                      -- NULL = halen devam ediyor
    CONSTRAINT uq_coach_career_unique
        UNIQUE (coach_id, team_id, start_date)
);

-- Bir kocun kariyeri tarihsel sirali.
CREATE INDEX IF NOT EXISTS idx_coach_career_coach_start
    ON coach_career (coach_id, start_date DESC);

-- Bir takimin gecmis ve mevcut koclari (career'dan join).
CREATE INDEX IF NOT EXISTS idx_coach_career_team_start
    ON coach_career (team_id, start_date DESC) WHERE team_id IS NOT NULL;

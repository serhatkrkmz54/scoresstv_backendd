-- V36 — TV yayin altyapisi.
--
-- 3 tablo:
-- 1. tv_channels    — Kanal master (beIN, S Sport, TRT, ESPN, Sky vb.)
-- 2. league_broadcasters — Lig+sezon+ulke icin varsayilan kanal(lar)
-- 3. match_broadcasts    — Mac bazinda override (derbi ozel kanal vs.)
--
-- Resolution: match_broadcasts > league_broadcasters > yok
-- Filtre: country_code ile, kullanici ulkesine gore.

-- ============================================================
-- TV Kanali (master)
-- ============================================================

CREATE TABLE tv_channels (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    name_tr VARCHAR(120),
    short_name VARCHAR(20),
    logo_url TEXT,
    /** 2-3 harfli ulke kodu — "TR", "GB", "US", "DE", "WORLDWIDE". */
    country_code VARCHAR(10) NOT NULL,
    /** Kanalin resmi sitesi — frontend logoya tikla -> kanal sitesi. */
    streaming_url TEXT,
    /** Tabii Spor / EXXEN gibi sadece dijital, TV'de yayin yok. */
    is_streaming_only BOOLEAN NOT NULL DEFAULT FALSE,
    /** Listelemede sirayi belirler (ana kanal once gosterilsin). */
    sort_order INT NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tv_channels_name_country UNIQUE (name, country_code)
);

CREATE INDEX idx_tv_channels_country ON tv_channels (country_code);
CREATE INDEX idx_tv_channels_active ON tv_channels (active);

COMMENT ON TABLE tv_channels IS 'TV yayin kanallari master listesi';
COMMENT ON COLUMN tv_channels.is_streaming_only IS 'Tabii/EXXEN/DAZN gibi sadece dijital';

-- ============================================================
-- Lig × Sezon × Ulke = Kanal(lar) — varsayilan yayinci
-- ============================================================

CREATE TABLE league_broadcasters (
    id BIGSERIAL PRIMARY KEY,
    league_id BIGINT NOT NULL,
    season INT NOT NULL,
    /** Hangi ulkede bu lig bu kanalda izlenir — "TR", "GB" vb. */
    country_code VARCHAR(10) NOT NULL,
    channel_id BIGINT NOT NULL,
    /** Sirali gosterim (ana kanal 1, alternatif 2). */
    sort_order INT NOT NULL DEFAULT 100,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_league_broadcasters_league
        FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
    CONSTRAINT fk_league_broadcasters_channel
        FOREIGN KEY (channel_id) REFERENCES tv_channels(id) ON DELETE CASCADE,
    CONSTRAINT uq_league_broadcasters UNIQUE (league_id, season, country_code, channel_id)
);

CREATE INDEX idx_league_broadcasters_lookup
    ON league_broadcasters (league_id, season, country_code);

COMMENT ON TABLE league_broadcasters IS 'Lig+sezon+ulke için varsayilan TV kanali atamasi';

-- ============================================================
-- Mac bazli yayin override
-- ============================================================

CREATE TABLE match_broadcasts (
    id BIGSERIAL PRIMARY KEY,
    fixture_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    country_code VARCHAR(10) NOT NULL,
    /** Sirali gosterim. */
    sort_order INT NOT NULL DEFAULT 100,
    notes TEXT,
    /**
     * Veri kaynagi: "MANUAL" (admin elle), "LIVESOCCERTV" (otomatik),
     * "IMPORT" (excel). UI ikonu icin.
     */
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_match_broadcasts_fixture
        FOREIGN KEY (fixture_id) REFERENCES fixtures(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_broadcasts_channel
        FOREIGN KEY (channel_id) REFERENCES tv_channels(id) ON DELETE CASCADE,
    CONSTRAINT uq_match_broadcasts UNIQUE (fixture_id, country_code, channel_id)
);

CREATE INDEX idx_match_broadcasts_fixture_country
    ON match_broadcasts (fixture_id, country_code);

COMMENT ON TABLE match_broadcasts IS 'Spesifik mac yayin override (lig defaultundan farkliysa)';
COMMENT ON COLUMN match_broadcasts.source IS 'MANUAL / LIVESOCCERTV / IMPORT';

-- ============================================================
-- Seed: en yaygin TR kanallari
-- ============================================================

INSERT INTO tv_channels (name, name_tr, short_name, country_code, sort_order, is_streaming_only)
VALUES
    ('beIN SPORTS 1', 'beIN SPORTS 1', 'BS1', 'TR', 10, FALSE),
    ('beIN SPORTS 2', 'beIN SPORTS 2', 'BS2', 'TR', 11, FALSE),
    ('beIN SPORTS 3', 'beIN SPORTS 3', 'BS3', 'TR', 12, FALSE),
    ('beIN SPORTS 4', 'beIN SPORTS 4', 'BS4', 'TR', 13, FALSE),
    ('beIN SPORTS MAX 1', 'beIN SPORTS MAX 1', 'BSM1', 'TR', 14, FALSE),
    ('beIN SPORTS MAX 2', 'beIN SPORTS MAX 2', 'BSM2', 'TR', 15, FALSE),
    ('S Sport', 'S Sport', 'SS', 'TR', 20, FALSE),
    ('S Sport 2', 'S Sport 2', 'SS2', 'TR', 21, FALSE),
    ('S Sport Plus', 'S Sport Plus', 'SSP', 'TR', 22, TRUE),
    ('TRT Spor', 'TRT Spor', 'TRT', 'TR', 30, FALSE),
    ('TRT Spor Yıldız', 'TRT Spor Yıldız', 'TRTY', 'TR', 31, FALSE),
    ('Tabii Spor', 'Tabii Spor', 'TAB', 'TR', 40, TRUE),
    ('EXXEN Spor', 'EXXEN Spor', 'EXX', 'TR', 50, TRUE),
    ('Tivibu Spor', 'Tivibu Spor', 'TVB', 'TR', 60, FALSE),
    ('A Spor', 'A Spor', 'ASP', 'TR', 70, FALSE),
    ('ATV', 'ATV', 'ATV', 'TR', 71, FALSE),
    ('Show TV', 'Show TV', 'SHW', 'TR', 72, FALSE);

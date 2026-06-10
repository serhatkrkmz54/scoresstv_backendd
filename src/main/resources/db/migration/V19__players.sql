-- Oyuncu master tablosu. Daha once player_id + player_name + player_photo
-- alanlari injury / league_top_players / fixture_player_stats /
-- fixture_lineup_players / fixture_events tablolarinda flat kayitliydi
-- (denormalized). Simdi tek bir master kayit olusturuyoruz; foto MinIO'ya
-- aynalanip o tabloda saklanacak. Diger tablolar player_id ile referans
-- vermeye devam eder (player_name/photo alanlari da kalmaya devam eder —
-- snapshot icin; ama UI photo'yu master tablodan alir).

CREATE TABLE IF NOT EXISTS players (
    id          BIGINT       PRIMARY KEY,                 -- API-Football player.id
    name        VARCHAR(255) NOT NULL,
    photo_url   VARCHAR(500),                              -- orijinal media.api-sports.io URL
    photo_key   VARCHAR(255),                              -- MinIO nesne anahtari (mirror sonrasi)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Aynalanmamis oyuncularin sorgusu (ImageMirrorService.mirrorPlayerPhotos):
-- WHERE photo_key IS NULL AND photo_url IS NOT NULL
CREATE INDEX IF NOT EXISTS idx_players_photo_key_null
    ON players (id) WHERE photo_key IS NULL AND photo_url IS NOT NULL;

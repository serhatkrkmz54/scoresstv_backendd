-- Oyuncu sakatlik / cezalik gecmisi. API:
--   GET /sidelined?player=X
--   GET /sidelined?players=A-B-C  (batch)
-- Yanit: flat liste {type, start, end}.
-- Takim sayfasinda: kadronun AKTIF (end >= bugun veya end null) sakatlik/
-- cezalik kayitlari gosterilir.

CREATE TABLE IF NOT EXISTS player_sidelined (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT       NOT NULL,            -- Player master
    type        VARCHAR(120) NOT NULL,            -- "Hip/Thigh Injury" / "Suspended" / ...
    start_date  DATE,
    end_date    DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Idempotency
    CONSTRAINT uq_player_sidelined_unique
        UNIQUE (player_id, type, start_date)
);

CREATE INDEX IF NOT EXISTS idx_player_sidelined_player_start
    ON player_sidelined (player_id, start_date DESC);

-- Sik sorgu: bu oyuncu su an sakat mi/cezali mi?
-- WHERE player_id = X AND (end_date IS NULL OR end_date >= CURRENT_DATE)
CREATE INDEX IF NOT EXISTS idx_player_sidelined_active
    ON player_sidelined (player_id) WHERE end_date IS NULL OR end_date >= '2020-01-01';

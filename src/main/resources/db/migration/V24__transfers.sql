-- Transfer kayitlari (flat). API:
--   GET /transfers?team=X
-- Yanit ic ice: her oyuncunun kariyer tarihindeki TUM transferleri doner.
-- Biz takimin satirlarini cikarip flat olarak buraya yazariz —
-- "transferleri WHERE team=X" sorgusunu hizli yapmak icin.
--
-- BIR SATIR = bir transfer hareketi. Hem in_team_id hem out_team_id
-- doludur (free agent transferinde de API kendi takimini koyar).
-- Soruda "takima gelenler" → WHERE in_team_id = X
-- Soruda "takimdan gidenler" → WHERE out_team_id = X
-- "Tum hareketler" → WHERE in_team_id = X OR out_team_id = X

CREATE TABLE IF NOT EXISTS transfers (
    id              BIGSERIAL PRIMARY KEY,
    player_id       BIGINT      NOT NULL,           -- Player master ile referans
    -- Snapshot (master tabloyla redundant ama join'siz okunsun)
    player_name     VARCHAR(255) NOT NULL,
    transfer_date   DATE,                            -- API "2019-07-15"; nadiren null
    -- "Free" / "Loan" / "$X.XM" gibi - API metni
    transfer_type   VARCHAR(50),
    in_team_id      BIGINT,                          -- Gittigi takim
    in_team_name    VARCHAR(150),
    in_team_logo    VARCHAR(500),
    out_team_id     BIGINT,                          -- Geldigi takim
    out_team_name   VARCHAR(150),
    out_team_logo   VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Idempotency: ayni transfer'i tekrar yazma
    CONSTRAINT uq_transfers_player_date_in_out
        UNIQUE (player_id, transfer_date, in_team_id, out_team_id)
);

-- Bir takimin transferlerini tarih sirali al (yeni → eski).
CREATE INDEX IF NOT EXISTS idx_transfers_in_team_date
    ON transfers (in_team_id, transfer_date DESC);
CREATE INDEX IF NOT EXISTS idx_transfers_out_team_date
    ON transfers (out_team_id, transfer_date DESC);

-- Bir oyuncunun tum kariyer transferleri.
CREATE INDEX IF NOT EXISTS idx_transfers_player_date
    ON transfers (player_id, transfer_date DESC);

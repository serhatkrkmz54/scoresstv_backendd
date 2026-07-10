-- Maç öncesi Bilyoner oran arşivi (value/backtest için — SADECE İÇERİDE).
-- Her fixture için açılış (~24s kala) ve kapanış (~90dk kala) snapshot'ı.
-- Bu satırlar hiçbir public uçtan servis EDİLMEZ; kullanıcıya gösterilmez.
CREATE TABLE fixture_odds_snapshot (
    id                 BIGSERIAL PRIMARY KEY,
    fixture_id         BIGINT NOT NULL REFERENCES fixtures(id) ON DELETE CASCADE,
    source             VARCHAR(20) NOT NULL DEFAULT 'bilyoner',
    snapshot_kind      VARCHAR(10) NOT NULL,          -- 'opening' | 'closing'
    captured_at        TIMESTAMPTZ NOT NULL,
    minutes_to_kickoff INTEGER,
    odd_home           DOUBLE PRECISION,
    odd_draw           DOUBLE PRECISION,
    odd_away           DOUBLE PRECISION,
    odd_over25         DOUBLE PRECISION,
    odd_under25        DOUBLE PRECISION,
    odd_btts_yes       DOUBLE PRECISION,
    odd_btts_no        DOUBLE PRECISION,
    CONSTRAINT uq_fixture_odds_snapshot UNIQUE (fixture_id, source, snapshot_kind)
);

CREATE INDEX ix_fixture_odds_snapshot_fixture ON fixture_odds_snapshot (fixture_id);

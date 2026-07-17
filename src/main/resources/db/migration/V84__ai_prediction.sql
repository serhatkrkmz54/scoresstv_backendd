-- AI Analiz isabet takibi: her maç için modelin MAÇTAN ÖNCE verdiği tahmini
-- kaydeder, maç bitince notlar. Aylık/yıllık/tüm-zaman isabet istatistikleri
-- (maç sonucu, alt/üst 2.5, karşılıklı gol, tam skor) buradan üretilir.
CREATE TABLE ai_prediction (
    fixture_id      BIGINT PRIMARY KEY,
    league_id       BIGINT,
    kickoff_at      TIMESTAMPTZ NOT NULL,

    -- Model anlık görüntüsü (maçtan önce)
    favorite        VARCHAR(8),      -- HOME | DRAW | AWAY | NULL (başabaş)
    home_win_pct    INT,
    draw_pct        INT,
    away_win_pct    INT,
    over25_pct      INT,
    btts_yes_pct    INT,
    exp_home        DOUBLE PRECISION,
    exp_away        DOUBLE PRECISION,
    expected_score  VARCHAR(8),      -- yaklaşık beklenen skor "2-1"
    confidence      VARCHAR(16),

    -- Türetilmiş tahminler (notlama kolaylığı)
    pick_result     VARCHAR(8),      -- HOME | DRAW | AWAY (favori; NULL ise notlanmaz)
    pick_ou         VARCHAR(8),      -- OVER | UNDER (2.5)
    pick_btts       VARCHAR(8),      -- YES | NO

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Notlama (maç bitince)
    graded          BOOLEAN NOT NULL DEFAULT FALSE,
    actual_home     INT,
    actual_away     INT,
    result_hit      BOOLEAN,
    ou_hit          BOOLEAN,
    btts_hit        BOOLEAN,
    exact_hit       BOOLEAN,
    graded_at       TIMESTAMPTZ
);

CREATE INDEX idx_ai_prediction_graded_kickoff ON ai_prediction (graded, kickoff_at);
CREATE INDEX idx_ai_prediction_kickoff ON ai_prediction (kickoff_at);

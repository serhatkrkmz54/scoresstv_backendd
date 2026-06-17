-- Maç sonucu tahmin oylaması (anonim). Kullanıcı/cihaz başına tek oy
-- (voter_id = anonim istemci kimliği), kickoff'a kadar değiştirilebilir.
-- choice: HOME / DRAW / AWAY. sport: FOOTBALL / BASKETBALL (şimdilik FOOTBALL).
CREATE TABLE fixture_prediction_votes (
    id          BIGSERIAL PRIMARY KEY,
    match_id    BIGINT       NOT NULL,
    sport       VARCHAR(20)  NOT NULL,
    voter_id    VARCHAR(64)  NOT NULL,
    choice      VARCHAR(8)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fixture_prediction_votes_unique UNIQUE (match_id, sport, voter_id)
);

-- Dağılım sorgusu için (match + sport bazında choice sayımları).
CREATE INDEX idx_prediction_votes_match
    ON fixture_prediction_votes (match_id, sport);

-- Basketbol/voleybol bildirimleri de outbox'tan geçsin (garantili teslim):
--   sport    : futbol/basketbol/voleybol ayrımı — worker gönderim yolunu seçer
--   team2_id : basketbol/voleybol topic hedefi iki takım ister (futbolda tek
--              team_id yetiyordu; deplasman takımı bu kolonda taşınır)
ALTER TABLE notification_outbox
    ADD COLUMN sport VARCHAR(12) NOT NULL DEFAULT 'football';
ALTER TABLE notification_outbox
    ADD COLUMN team2_id BIGINT;

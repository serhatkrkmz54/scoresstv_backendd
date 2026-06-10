-- Takim "covered" bayragi — periyodik joblar (DailyTeamRefreshJob) hangi
-- takimlari kapsayacagini buradan secer. Lig coverage benzeri: ADMIN
-- popüler 50-100 takimi covered isaretler; geri kalanlar lazy sync ile
-- kullanici ziyaretiyle dolar.
ALTER TABLE teams ADD COLUMN covered BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_teams_covered ON teams (covered) WHERE covered = TRUE;

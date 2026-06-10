-- Sezon basina API-Football kapsam (coverage) bilgileri.
-- /leagues yaniti her sezon icin "bu sezonun hangi verileri var" doner;
-- bunlari saklayip lazy sync ve UI'da "veri yok" gostergesi icin kullaniriz.
-- Coverage degerleri sezon basladiktan sonra dolar; baslamadan once
-- hepsi false olur. UI bu bayraklara bakip "Top scorers henuz yok" der.

ALTER TABLE seasons
    ADD COLUMN IF NOT EXISTS coverage_standings BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_events BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_lineups BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_stats_fixtures BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_stats_players BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_players BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_top_scorers BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_top_assists BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_top_cards BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_injuries BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_predictions BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS coverage_odds BOOLEAN NOT NULL DEFAULT false;

-- Sik aranan: bir ligin sezonlari yeni-eski sirayla. Lig detay sayfasi
-- dropdown'i icin partial index gerekmez; orderby query plan'i index'i kullanir.
CREATE INDEX IF NOT EXISTS idx_seasons_league_year_desc
    ON seasons (league_id, season_year DESC);

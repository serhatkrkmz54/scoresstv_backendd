-- V48'de last_notified_period SMALLINT olarak oluşturuldu; entity int (INTEGER)
-- bekliyor → Hibernate schema validation hatası. Kolonu INTEGER'a genişlet
-- (SMALLINT→INTEGER güvenli, veri kaybı yok).
ALTER TABLE basketball_games
    ALTER COLUMN last_notified_period TYPE INTEGER;

-- V34 — Standings UNIQUE constraint'ine group_name ekle.
--
-- Neden: API-Football bazi kupalarda (TR Kupasi gibi) ayni takimi iki gruba
-- kor: gercek grubu (orn. Group B) + 3.lik ranking grubu (Group D). Eski
-- UNIQUE(league_id, season, team_id) ikinci insert'te violation atip TUM
-- transaction'i rollback ediyordu → standings tablosu bos kaliyordu.
--
-- Fix: UNIQUE'a group_name de dahil edilir. NULL grup adlari (ulusal lig
-- gibi) icin Postgres NULL'lari distinct sayar — pratik olarak ulusal
-- liglerde takim basina sadece 1 satir oldugu icin sorun yok.

ALTER TABLE standings
    DROP CONSTRAINT IF EXISTS uq_standings_league_season_team;

ALTER TABLE standings
    ADD CONSTRAINT uq_standings_league_season_team_group
        UNIQUE (league_id, season, team_id, group_name);

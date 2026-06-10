-- ADMIN manuel bas antrenor override'i.
--
-- Sebep: API-Football'un /coachs?team=X yaniti zaman zaman stale kalir:
-- antrenor degisiminden sonra hala eski antrenoru "end=null" olarak listeler.
-- Lineup-based picker sezon ici icin guvenilirdir ama OFF-SEASON'da (TR'de
-- Mayis sonu - Agustos arasi ~3 ay maç yok) lineup eski kalir.
--
-- Bu kolon ADMIN'in manuel olarak "su anda bu takimin bas antrenoru sudur"
-- demesini saglar. Picker oncelik sirasi:
--   1. head_coach_override_id (admin manuel — varsa daima kazanir)
--   2. lineup (son macta bench'te kim oturmus)
--   3. /coachs?team= kural fallback (end=null + en yeni start)
--
-- Override'i kaldirmak icin NULL'a set edilir.
ALTER TABLE teams
    ADD COLUMN head_coach_override_id BIGINT NULL REFERENCES coaches(id);

CREATE INDEX idx_teams_head_coach_override
    ON teams (head_coach_override_id) WHERE head_coach_override_id IS NOT NULL;

-- Maç durumu (başladı/bitti) bildirimleri için TAM-BİR-KEZ durumu.
--
-- Önceki tasarım kickoff/final'i tick'ler arası "diff" ile tespit ediyordu;
-- başka bir yol (detay lazy-sync) durumu önce ilerletirse geçiş kaçıyor ve
-- bildirim hiç gitmiyordu. Bu kolonlar + atomik UPDATE ... WHERE IS NULL ile
-- her geçiş tam-bir-kez işlenir (restart/çok-yol/yarış bağımsız).

ALTER TABLE fixtures ADD COLUMN notif_kickoff_at TIMESTAMPTZ;
ALTER TABLE fixtures ADD COLUMN notif_final_at   TIMESTAMPTZ;

-- Backfill: deploy ANINDA zaten başlamış/bitmiş maçlara "başladı/bitti"
-- push'u ATMAYALIM. Halen canlı veya bitmiş tüm maçların kickoff flag'i set;
-- bitmiş olanların final flag'i de set.
UPDATE fixtures SET notif_kickoff_at = now()
  WHERE status_short IN ('1H','HT','2H','ET','BT','P','SUSP','INT','LIVE','FT','AET','PEN');

UPDATE fixtures SET notif_final_at = now()
  WHERE status_short IN ('FT','AET','PEN');

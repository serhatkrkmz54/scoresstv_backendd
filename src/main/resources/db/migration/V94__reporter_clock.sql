-- Muhabir maçlarında otomatik dakika: yarının başladığı an. 1H başlarken ve
-- 2H başlarken set edilir; ManualLiveClockJob elapsed'i buradan hesaplar.
ALTER TABLE fixtures ADD COLUMN manual_phase_start TIMESTAMPTZ;

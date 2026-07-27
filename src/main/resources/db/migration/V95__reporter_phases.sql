-- Muhabir canlı konsolu: tam faz makinesi desteği.
-- manual_phase_base : yarı/uzatma başlarken dakika taban değeri (1, 46, 91, 106
--                     veya duraklatmadan devamda kaldığı dakika). ManualLiveClockJob
--                     elapsed'i base + geçen dakika olarak işletir.
-- manual_stoppage   : hakemin ilan ettiği uzatma (+dk). Saat, uzatmayı bu değerde
--                     tutar (yoksa 15 dk tavan).
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS manual_phase_base INT;
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS manual_stoppage INT;

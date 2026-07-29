-- Maçın CANLI'dan FİNAL statüye ilk geçtiği an. "Post-finish settling"
-- penceresi bu andan işler: penaltı finali / geç VAR-skor düzeltmesi / eksik
-- son event bitişten sonra gelirse, LiveTicker bu pencere boyunca maçı yeniden
-- çekip doğru final veriyi yazar + anında yayınlar (donmuş skor sorunu çözülür).
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS finished_at timestamptz;

-- Settling seçim sorgusu: status_short IN (final) AND finished_at > cutoff.
-- Kısmi index — yalnız settling penceresindeki (finished_at dolu) satırlar.
CREATE INDEX IF NOT EXISTS idx_fixtures_settling
    ON fixtures (finished_at)
    WHERE finished_at IS NOT NULL;

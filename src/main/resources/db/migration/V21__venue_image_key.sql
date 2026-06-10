-- Stadyum gorseli MinIO mirror'i icin nesne anahtari kolonu.
-- venues tablosu zaten image_url'i tutuyor (API'den ham URL); image_key
-- aynalanma sonrasi MinIO key'i tutar — ImageMirrorService.mirrorVenueImages
-- batch ile doldurur.

ALTER TABLE venues
    ADD COLUMN IF NOT EXISTS image_key VARCHAR(255);

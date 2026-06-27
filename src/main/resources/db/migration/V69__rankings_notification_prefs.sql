-- Sıralama (FIFA / UEFA) bildirim altyapısı.
--
-- İki hedefleme modeli:
--   1) FIFA + UEFA Ülke sıralaması → cihazın ülkesine göre (country_code).
--   2) UEFA Kulüp sıralaması → kullanıcının favori (takip ettiği) takımına göre.
--
-- Cihaz ülke kodu: ISO-3 / futbol federasyon kodu (örn. "TUR", "ENG", "ITA").
-- FIFA ve UEFA Ülke sıralamaları aynı 3-harfli kodu kullanır; eşleşme tutarlı.
-- Mobile, profil ülkesinden (yoksa cihaz locale'inden) türetip POST eder.
ALTER TABLE mobile_device_tokens
    ADD COLUMN country_code varchar(10);

-- FIFA + UEFA Ülke sıralama bildirimleri toggle. Mevcut cihazlar için AÇIK
-- (diğer bildirim tipleriyle tutarlı varsayılan).
ALTER TABLE mobile_device_tokens
    ADD COLUMN notify_rankings_country boolean NOT NULL DEFAULT true;

-- Ülkeye göre alıcı çözümü için kısmi index (yalnız ülkesi olan cihazlar).
CREATE INDEX IF NOT EXISTS idx_mobile_device_tokens_country
    ON mobile_device_tokens (country_code) WHERE country_code IS NOT NULL;

-- UEFA Kulüp sıralaması değişince favori takım için bildirim toggle.
-- Mevcut takip kayıtları için AÇIK (diğer notify_* kolonlarıyla tutarlı).
ALTER TABLE user_notification_prefs
    ADD COLUMN notify_rankings_club boolean NOT NULL DEFAULT true;

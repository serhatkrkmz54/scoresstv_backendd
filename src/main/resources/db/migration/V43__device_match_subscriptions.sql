-- V43 — Mac bazli favori bildirim abonelikleri.
--
-- Cihaz favori mac listesi mobile tarafinda lokal (SharedPreferences) ama
-- bildirim alabilmesi icin backend de bilmek zorunda — uygulama kapaliyken
-- FCM push'lari bu tabloya bakarak gonderiyoruz.
--
-- <h3>Akis</h3>
-- 1. Mobile favori toggle yapar -> lokalde guncellenir
-- 2. Mobile POST /api/v1/mobile/favorite-matches/sync ile tum listeyi yollar
-- 3. Backend bu cihaz icin eski tum subscription'lari siler + yenilerini yazar
-- 4. NotificationDispatcher: event/kickoff/final geldiginde
--    user_notification_prefs (takim takibinden) + bu tablo (favori macten)
--    BIRLIKTE recipient olarak ekler
--
-- <h3>Default bildirim seti</h3>
-- Favori mac = "tum onemli olaylar acik" (gol/kart/penalti/basladi/bitti).
-- Mac-bazli kapatma toggles'i ileride eklenebilir; simdilik basit ve net.

CREATE TABLE device_match_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    device_token_id BIGINT NOT NULL
        REFERENCES mobile_device_tokens(id) ON DELETE CASCADE,
    fixture_id BIGINT NOT NULL
        REFERENCES fixtures(id) ON DELETE CASCADE,
    -- BaseEntity audit kolonlari (entity'nin gerektirdigi alanlar)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_token_id, fixture_id)
);

-- Dispatcher icin: "bu macin recipient'lari kim?" sorgusu.
-- Olay gelen mac id ile cihaz listesi cikartiriliriz.
CREATE INDEX idx_device_match_subs_fixture ON device_match_subscriptions (fixture_id);

-- Cihazin tum favori listesini okumak icin (replace sync oncesi delete).
CREATE INDEX idx_device_match_subs_device ON device_match_subscriptions (device_token_id);

-- V39 — Mobil cihaz FCM token kayitlari.
--
-- Anonim cihaz tabanli model: kullanici hesabi yok, FCM token unique device
-- identity. Tek bir cihaz = tek bir kayit. Uninstall + reinstall yeni token
-- alir; eski token sonunda silinir (auto-cleanup ileride).
--
-- POST /api/v1/mobile/device-tokens endpoint'i bu tabloyu yazar:
-- - yeni token: INSERT
-- - mevcut token: UPDATE last_seen_at + locale/app_version
--
-- Notification gonderimi: notification_dispatcher token listesi bu tablodan
-- ceker, ilgili kullanicinin (device'in) tercihlerine bakar.

CREATE TABLE mobile_device_tokens (
    id BIGSERIAL PRIMARY KEY,
    /** FCM tarafindan verilen unique token. Cihaz icin de identity. */
    fcm_token TEXT NOT NULL UNIQUE,
    /** 'android' | 'ios' — gelecekte 'web' eklenebilir. */
    platform VARCHAR(20) NOT NULL,
    /** App store'daki versiyon — debug icin yararli, opsiyonel. */
    app_version VARCHAR(20),
    /** Kullanici uygulama dili — bildirim icerigi bu dilde uretilir. */
    locale VARCHAR(10) NOT NULL DEFAULT 'tr',
    /** Her POST cagrisinda guncellenir — eski cihazlari tespit etmeye yarar. */
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- BaseEntity audit kolonlari
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cleanup taramalari icin (gelecekte: 90gun gormeyenleri sil).
CREATE INDEX idx_mobile_device_tokens_last_seen
    ON mobile_device_tokens (last_seen_at);

CREATE INDEX idx_mobile_device_tokens_platform
    ON mobile_device_tokens (platform);

-- ===================================================================
-- V3 - Calisma zamaninda ADMIN tarafindan degistirilebilen ayarlar
-- ===================================================================

CREATE TABLE app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Brute-force koruma varsayilanlari: 5 hatali deneme -> 15 dakika kilit
INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('auth.max-failed-attempts', '5'),
    ('auth.lockout-minutes', '15');

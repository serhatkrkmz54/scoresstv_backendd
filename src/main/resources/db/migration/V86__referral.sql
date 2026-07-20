-- Davet / referans sistemi.
-- Her kullaniciya (talep edildiginde uretilen) benzersiz davet kodu + davet
-- kayitlari tablosu. Yeni kullanici bir kodu EN FAZLA bir kez kullanabilir.

ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code VARCHAR(12);

-- Kod benzersiz (null'lar haric — kodu henuz uretilmemis kullanicilar).
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_referral_code
    ON users (referral_code)
    WHERE referral_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS referrals (
    id           BIGSERIAL PRIMARY KEY,
    referrer_id  BIGINT      NOT NULL,   -- daveti veren (kodu paylasan)
    referee_id   BIGINT      NOT NULL,   -- kodu kullanan yeni kullanici
    reward_each  INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Bir yeni kullanici yalnizca TEK davet kullanabilir.
    CONSTRAINT uq_referrals_referee UNIQUE (referee_id)
);

CREATE INDEX IF NOT EXISTS idx_referrals_referrer ON referrals (referrer_id);

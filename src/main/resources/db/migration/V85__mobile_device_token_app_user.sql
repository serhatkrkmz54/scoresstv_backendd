-- Cihaz token'ını (opsiyonel) giriş yapmış kullanıcıya bağlar.
-- Anonim cihaz kaydı korunur (app_user_id NULL kalabilir); kullanıcı giriş
-- yaptığında register isteğine JWT eklendiği için backend user id'yi doldurur.
-- Kullanım: oyun sonucu gibi KULLANICIYA ÖZEL push'ları (şu kadar tahminin
-- tuttu, şu kadar puan kazandın) doğru cihazlara göndermek için.
ALTER TABLE mobile_device_tokens
    ADD COLUMN IF NOT EXISTS app_user_id BIGINT;

-- Kullanıcıya göre aktif cihazları hızlı bulmak için (bildirim hedefleme).
CREATE INDEX IF NOT EXISTS idx_mobile_device_tokens_app_user
    ON mobile_device_tokens (app_user_id)
    WHERE app_user_id IS NOT NULL;

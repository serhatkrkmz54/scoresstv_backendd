-- ===================================================================
-- V2 - Ilk ADMIN kullanicisi
-- E-posta : admin@scorestv.com
-- Sifre   : ChangeMe!2026   <-- ILK GIRISTEN SONRA MUTLAKA DEGISTIR
-- Hash    : BCrypt
-- ===================================================================

INSERT INTO users (email, password, display_name, role, enabled)
VALUES (
    'admin@scorestv.com',
    '$2b$12$Cy5gTLPWGe9PE7RSA37wlegsRqFs3xS8f.Jk0mNwwcRT1nhzCtDFO',
    'Scorestv Admin',
    'ADMIN',
    TRUE
);

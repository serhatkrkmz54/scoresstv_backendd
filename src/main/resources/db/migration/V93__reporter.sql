-- ============================================================
-- Saha Muhabiri programı — kullanıcıların API kapsamı dışındaki ligleri
-- (amatör kümeler vb.) elle girmesi.
--
-- TASARIM GARANTİSİ: Manuel kayıtlar AYNI tablolarda ama AYRI id uzayında
-- (900.000.000+) yaşar. API senkronu kendi API id'leriyle upsert ettiği için
-- manuel kayıtlara matematiksel olarak dokunamaz; manuel kayıtlar da API
-- verisini etkilemez. source kolonu yalnız rozet/filtre/koruma içindir.
-- ============================================================

-- 1) Kaynak ayrımı: api | manual
ALTER TABLE leagues  ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'api';
ALTER TABLE teams    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'api';
ALTER TABLE fixtures ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'api';

-- 2) Manuel varlık id uzayı — API-Football id'leriyle çakışmaz (API id'leri
-- ~10M mertebesinde; 900M üstü tamamen bize ait).
CREATE SEQUENCE manual_entity_seq START WITH 900000001;

-- 3) Muhabir başvuruları ("X ligini ben girerim").
CREATE TABLE reporter_applications (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    league_name  VARCHAR(150) NOT NULL,
    region       VARCHAR(150),
    message      VARCHAR(1000) NOT NULL,
    -- PENDING | APPROVED | REJECTED
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    reviewed_by  BIGINT       REFERENCES users(id),
    reviewed_at  TIMESTAMPTZ,
    review_note  VARCHAR(500),
    -- Onayda oluşturulan manuel ligin id'si.
    league_id    BIGINT       REFERENCES leagues(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_rep_app_status ON reporter_applications(status, created_at DESC);
CREATE INDEX idx_rep_app_user ON reporter_applications(user_id);

-- 4) Muhabir atamaları — kullanıcı ↔ manuel lig yetkisi.
CREATE TABLE reporter_assignments (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    league_id  BIGINT      NOT NULL REFERENCES leagues(id),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_rep_assignment UNIQUE (user_id, league_id)
);
CREATE INDEX idx_rep_assign_user ON reporter_assignments(user_id) WHERE active;

-- 5) Manuel lig ↔ takım üyeliği (futbolda takım-lig junction'ı yok; muhabirin
-- lig sayfasında takım listesi için gerekir).
CREATE TABLE reporter_league_teams (
    league_id  BIGINT NOT NULL REFERENCES leagues(id),
    team_id    BIGINT NOT NULL REFERENCES teams(id),
    PRIMARY KEY (league_id, team_id)
);

-- 6) "Hata bildir" özelliği kaldırıldı — muhabir modeliyle değiştirildi.
DROP TABLE IF EXISTS data_contributions;

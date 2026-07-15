-- ScoresTV V79 — Oyuncu düello tahmin oyunu ("Scores Coin").
--
-- Ücretsiz, beceri temelli tahmin oyunu: aynı mevkideki iki oyuncu belirli bir
-- istatistikte kapışır (rating, gol, asist, kurtarış, ikili mücadele...).
-- Kullanıcı kazananı tahmin eder; dönem (hafta/ay/sezon) sonunda gerçek maç
-- istatistikleriyle (fixture_player_stats) çözülür ve Scores Coin dağıtılır.
-- Para/giriş ücreti YOK; coin nakde çevrilemez (kumar değil, fantasy oyunu).

-- ============================================================
-- 1) YARIŞMA (bir dönem: haftalık/aylık/sezonluk) — düelloları gruplar
-- ============================================================
CREATE TABLE game_competition (
    id          BIGSERIAL PRIMARY KEY,
    scope       VARCHAR(16)  NOT NULL,                 -- WEEKLY | MONTHLY | SEASON
    title       VARCHAR(160) NOT NULL,
    sport       VARCHAR(16)  NOT NULL DEFAULT 'FOOTBALL',
    season      INTEGER,
    league_id   BIGINT,                                -- opsiyonel: lig-bazlı yarışma
    start_at    TIMESTAMPTZ  NOT NULL,                 -- çözümleme penceresi başı
    end_at      TIMESTAMPTZ  NOT NULL,                 -- çözümleme penceresi sonu
    lock_at     TIMESTAMPTZ  NOT NULL,                 -- tahminler bu andan sonra kilitli
    status      VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT | OPEN | LOCKED | RESOLVED
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_game_competition_status ON game_competition (status, lock_at);

-- ============================================================
-- 2) DÜELLO (aynı mevkiden iki oyuncu, bir istatistikte kapışır)
--    Oyuncu bilgisi denormalize — UI ekstra join yapmadan gösterir.
-- ============================================================
CREATE TABLE game_duel (
    id              BIGSERIAL PRIMARY KEY,
    competition_id  BIGINT       NOT NULL REFERENCES game_competition (id) ON DELETE CASCADE,
    position        VARCHAR(8)   NOT NULL,             -- GK | DEF | MID | FWD
    metric          VARCHAR(24)  NOT NULL,             -- RATING | GOALS | ASSISTS | ...
    direction       VARCHAR(8)   NOT NULL DEFAULT 'HIGHER', -- HIGHER | LOWER (kart/faul LOWER)
    league_id       BIGINT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,

    player_a_id        BIGINT      NOT NULL,
    player_a_name      VARCHAR(120),
    player_a_photo     VARCHAR(255),
    player_a_team      VARCHAR(120),
    player_a_team_logo VARCHAR(255),

    player_b_id        BIGINT      NOT NULL,
    player_b_name      VARCHAR(120),
    player_b_photo     VARCHAR(255),
    player_b_team      VARCHAR(120),
    player_b_team_logo VARCHAR(255),

    status      VARCHAR(12)  NOT NULL DEFAULT 'OPEN',  -- OPEN | RESOLVED | VOID
    winner      VARCHAR(8),                            -- A | B | DRAW | VOID
    value_a     NUMERIC(8,2),                          -- çözümlenen değer (rating veya sayı)
    value_b     NUMERIC(8,2),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_game_duel_competition ON game_duel (competition_id, sort_order);

-- ============================================================
-- 3) KULLANICI TAHMİNİ (giriş zorunlu — user_id gerçek hesap)
--    Düello başına tek tahmin; kilit anına kadar değiştirilebilir.
-- ============================================================
CREATE TABLE game_pick (
    id             BIGSERIAL PRIMARY KEY,
    competition_id BIGINT      NOT NULL REFERENCES game_competition (id) ON DELETE CASCADE,
    duel_id        BIGINT      NOT NULL REFERENCES game_duel (id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pick           VARCHAR(4)  NOT NULL,               -- A | B
    correct        BOOLEAN,                            -- çözümlenene dek NULL
    coins_awarded  INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_game_pick UNIQUE (duel_id, user_id)
);
CREATE INDEX ix_game_pick_user ON game_pick (user_id);
CREATE INDEX ix_game_pick_competition ON game_pick (competition_id, user_id);

-- ============================================================
-- 4) SCORES COIN DEFTERİ (kaynak-doğru; her hareket burada)
-- ============================================================
CREATE TABLE scores_coin_ledger (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delta         INTEGER     NOT NULL,                -- +kazanç / -harcama
    balance_after BIGINT      NOT NULL,
    reason        VARCHAR(32) NOT NULL,                -- PICK_WIN | STREAK_BONUS | ...
    ref_type      VARCHAR(24),                         -- DUEL | COMPETITION
    ref_id        BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_coin_ledger_user ON scores_coin_ledger (user_id, created_at DESC);

-- ============================================================
-- 5) KULLANICI OYUN İSTATİSTİĞİ (hızlı sıralama + profil özeti)
--    balance = harcanabilir cüzdan; lifetime = sıralama (kazanılan toplam).
-- ============================================================
CREATE TABLE user_game_stat (
    user_id        BIGINT      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    coin_balance   BIGINT      NOT NULL DEFAULT 0,
    lifetime_coins BIGINT      NOT NULL DEFAULT 0,
    total_picks    INTEGER     NOT NULL DEFAULT 0,
    correct_picks  INTEGER     NOT NULL DEFAULT 0,
    current_streak INTEGER     NOT NULL DEFAULT 0,
    best_streak    INTEGER     NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_game_stat_leaderboard ON user_game_stat (lifetime_coins DESC);

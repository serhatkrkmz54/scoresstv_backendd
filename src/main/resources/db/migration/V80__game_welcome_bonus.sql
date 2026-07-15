-- ScoresTV V80 — Mevcut TÜM üyelere geriye dönük "Hoşgeldin Bonusu" (10 Scores Coin).
--
-- Oyun (V79) yeni eklendi; henüz kimse oynamadı → her üye 0'dan başlar, 10'a çıkar.
-- Bundan SONRA kayıt olanlara bonus, GameWelcomeBonusListener (AFTER_COMMIT) verir.
-- İdempotent: yalnız WELCOME_BONUS'u / stat satırı OLMAYAN üyelere işler.

-- 1) Cüzdan: her üye için 10 coin'lik oyun istatistiği oluştur (yoksa).
INSERT INTO user_game_stat (user_id, coin_balance, lifetime_coins, updated_at)
SELECT u.id, 10, 10, now()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_game_stat s WHERE s.user_id = u.id
);

-- 2) Ledger (kaynak-doğru hareket kaydı): her üye için tek WELCOME_BONUS satırı (yoksa).
INSERT INTO scores_coin_ledger (user_id, delta, balance_after, reason, ref_type, ref_id, created_at)
SELECT u.id, 10, 10, 'WELCOME_BONUS', 'USER', u.id, now()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM scores_coin_ledger l
    WHERE l.user_id = u.id AND l.reason = 'WELCOME_BONUS'
);

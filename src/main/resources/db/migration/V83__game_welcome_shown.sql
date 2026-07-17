-- Hoşgeldin kutlaması: sunucu-tarafı "gösterildi" bayrağı. Cihazdan bağımsız —
-- yeniden kurulumda / farklı cihazda bile ömür boyu YALNIZ 1 kez gösterilir.
ALTER TABLE user_game_stat
    ADD COLUMN welcome_shown BOOLEAN NOT NULL DEFAULT FALSE;

-- Mevcut satırı olan üyeler zaten oyunla tanışmış (yerel bayrakla görmüş
-- olabilirler) → yeniden gösterme. Bundan sonra oluşacak yeni satırlar
-- (yeni üyeler) varsayılan FALSE ile gelir ve bir kez görür.
UPDATE user_game_stat SET welcome_shown = TRUE;

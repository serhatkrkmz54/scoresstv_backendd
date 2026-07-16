-- V82 — Oyun yarışması İngilizce başlık (i18n). Panelden TR + EN girilir; mobil
-- kullanıcının diline göre gösterir (EN boşsa TR'ye düşer).
ALTER TABLE game_competition ADD COLUMN title_en VARCHAR(160);

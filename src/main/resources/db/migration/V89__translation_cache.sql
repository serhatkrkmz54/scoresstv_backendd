-- Otomatik makine-çevirisi (DeepL) sonuç cache'i.
--
-- API-Football'dan İngilizce gelen sabit metinlerin (sakatlık sebebi, puan
-- durumu açıklaması, istatistik adı, round, transfer türü...) TR karşılığı
-- FootballMessages'ta 3 katmanlı çözülür: (1) messages_tr.properties sözlüğü,
-- (2) akıllı parser, (3) ham İngilizce fallback. Bu tablo 3. katmanı DeepL ile
-- doldurur: sözlük+parser tutmazsa DeepL'e sorulur, sonuç BURAYA yazılır ve
-- bir daha çevrilmez. Sözlük hâlâ önce çalışır → elle girilen çeviriler kazanır;
-- DeepL yalnız boşluğu doldurur.
--
-- Anahtar: (category, source_text, target_lang) → translated.

CREATE TABLE translation_cache (
    id           BIGSERIAL     PRIMARY KEY,
    category     VARCHAR(40)   NOT NULL,   -- 'injury_reason','standing_desc','statistic_type',...
    source_text  VARCHAR(500)  NOT NULL,   -- ham İngilizce kaynak (trim'li)
    target_lang  VARCHAR(5)    NOT NULL,   -- 'tr'
    translated   VARCHAR(500)  NOT NULL,   -- çevrilmiş metin
    provider     VARCHAR(20)   NOT NULL DEFAULT 'deepl',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_translation_cache UNIQUE (category, source_text, target_lang)
);

-- Serving katmanı bu üçlü ile okur (unique index zaten kapsıyor).

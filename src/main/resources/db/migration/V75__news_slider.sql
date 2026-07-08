-- Haber slider'i (web /haberler sayfasi ust bandi) — panelden yonetilir.
-- in_slider: haber slider'da gosterilsin mi. slider_order: kucukten buyuge sira.
ALTER TABLE news_articles
    ADD COLUMN in_slider    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN slider_order INTEGER NOT NULL DEFAULT 0;

-- Slider sorgusu (dil + inSlider + sira) icin.
CREATE INDEX idx_news_articles_slider
    ON news_articles (in_slider, lang, slider_order);

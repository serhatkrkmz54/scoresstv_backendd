-- Haber ↔ mac (fixture) baglantisi. Mac-raporu (RESULT/PREVIEW) haberleri
-- belirli bir maca baglanabilir; public liste bu maca gore filtrelenir ve
-- push bildiriminde o maci favorileyen cihazlara da ulasir.
--
-- fixture_id API-Football fixture id'sidir (harici kaynakli). Diger link
-- tablolari (article_team_links, article_league_links...) gibi hard FK
-- KONULMAZ — yalniz news_articles'a CASCADE FK vardir.
CREATE TABLE article_fixture_links (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT    NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    fixture_id BIGINT    NOT NULL,
    CONSTRAINT uq_article_fixture UNIQUE (article_id, fixture_id)
);
CREATE INDEX idx_article_fixture_links_fixture ON article_fixture_links (fixture_id);

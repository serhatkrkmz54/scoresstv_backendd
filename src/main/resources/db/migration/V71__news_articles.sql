-- Haber (news) modulu — dil basina tek satir (lang = 'tr' | 'en').
-- Public PUBLISHED haberleri okur; EDITOR olusturur/gunceller/yayinlar;
-- yalniz ADMIN siler. TR ve EN versiyonlar translation_group_id ile eslesir
-- (NULL = tekil, ceviri esi yok). Slug global benzersizdir.
CREATE TABLE news_articles (
    id                   BIGSERIAL     PRIMARY KEY,
    slug                 VARCHAR(255)  NOT NULL UNIQUE,
    lang                 VARCHAR(2)    NOT NULL,
    translation_group_id BIGINT,
    title                VARCHAR(255)  NOT NULL,
    summary              VARCHAR(600),
    body                 TEXT          NOT NULL,
    cover_image_key      VARCHAR(255),
    status               VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    category             VARCHAR(32),
    is_breaking          BOOLEAN       NOT NULL DEFAULT false,
    is_featured          BOOLEAN       NOT NULL DEFAULT false,
    sport                VARCHAR(16)   NOT NULL DEFAULT 'FOOTBALL',
    author_id            BIGINT        NOT NULL REFERENCES users(id),
    source               VARCHAR(64)   NOT NULL DEFAULT 'MANUAL',
    source_url           VARCHAR(1024),
    published_at         TIMESTAMPTZ,
    view_count           BIGINT        NOT NULL DEFAULT 0,
    reading_minutes      INT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);

-- Public liste: yayinda + en yeni once.
CREATE INDEX idx_news_articles_status_published
    ON news_articles (status, published_at DESC);
CREATE INDEX idx_news_articles_lang
    ON news_articles (lang);
CREATE INDEX idx_news_articles_translation_group
    ON news_articles (translation_group_id);
CREATE INDEX idx_news_articles_category
    ON news_articles (category);
CREATE INDEX idx_news_articles_sport
    ON news_articles (sport);
-- Aktif (silinmemis) kayitlar icin kismi indeks — liste sorgulari hep
-- deleted_at IS NULL filtreler.
CREATE INDEX idx_news_articles_active
    ON news_articles (id) WHERE deleted_at IS NULL;

-- Bagli varlik link tablolari. entity_id API-Football ID'sidir; teams/leagues/
-- players tablolarinin PK'lari dis kaynakli (uretilmez) oldugu ve haberin bagli
-- oldugu varlik henuz senkronlanmamis olabilecegi icin hard FK KONULMAZ —
-- yalniz news_articles'a CASCADE FK vardir.
CREATE TABLE article_team_links (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT    NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    team_id    BIGINT    NOT NULL,
    CONSTRAINT uq_article_team UNIQUE (article_id, team_id)
);
CREATE INDEX idx_article_team_links_team ON article_team_links (team_id);

CREATE TABLE article_league_links (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT    NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    league_id  BIGINT    NOT NULL,
    CONSTRAINT uq_article_league UNIQUE (article_id, league_id)
);
CREATE INDEX idx_article_league_links_league ON article_league_links (league_id);

CREATE TABLE article_country_links (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT    NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    country_id BIGINT    NOT NULL,
    CONSTRAINT uq_article_country UNIQUE (article_id, country_id)
);
CREATE INDEX idx_article_country_links_country ON article_country_links (country_id);

CREATE TABLE article_player_links (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT    NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    player_id  BIGINT    NOT NULL,
    CONSTRAINT uq_article_player UNIQUE (article_id, player_id)
);
CREATE INDEX idx_article_player_links_player ON article_player_links (player_id);

-- Denetim gunlugu — kim ne zaman hangi eylemi yapti (create/update/publish...).
CREATE TABLE news_audit_log (
    id         BIGSERIAL   PRIMARY KEY,
    article_id BIGINT,
    actor_id   BIGINT,
    action     VARCHAR(32) NOT NULL,
    at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    meta       VARCHAR(1024)
);
CREATE INDEX idx_news_audit_log_article ON news_audit_log (article_id);

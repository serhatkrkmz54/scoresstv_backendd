-- Yorum sistemi: fixture (mac) basina kullanici yorumlari + begeni.
-- Kullanici girisi gereklidir (FK users); silme yetki sahibinde + admin.

CREATE TABLE fixture_comments (
    id          BIGSERIAL PRIMARY KEY,
    fixture_id  BIGINT      NOT NULL REFERENCES fixtures(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content     TEXT        NOT NULL CHECK (length(content) BETWEEN 1 AND 2000),
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fixture_comments_fixture
    ON fixture_comments(fixture_id, created_at DESC)
    WHERE deleted = FALSE;

CREATE INDEX idx_fixture_comments_user
    ON fixture_comments(user_id, created_at DESC);

-- Yorum begeni — (comment_id, user_id) unique. Tekrar begeni = unlike.
CREATE TABLE fixture_comment_likes (
    id          BIGSERIAL PRIMARY KEY,
    comment_id  BIGINT      NOT NULL REFERENCES fixture_comments(id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_comment_like_user UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_fixture_comment_likes_comment
    ON fixture_comment_likes(comment_id);

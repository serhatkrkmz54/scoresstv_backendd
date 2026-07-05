package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArticleFixtureLinkRepository extends JpaRepository<ArticleFixtureLink, Long> {

    List<ArticleFixtureLink> findByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);

    /** Bir maca bagli haber id'leri — dispatcher/liste sorgulari icin. */
    @Query("SELECT l.articleId FROM ArticleFixtureLink l WHERE l.fixtureId = :fixtureId")
    List<Long> findArticleIdsByFixtureId(@Param("fixtureId") Long fixtureId);
}

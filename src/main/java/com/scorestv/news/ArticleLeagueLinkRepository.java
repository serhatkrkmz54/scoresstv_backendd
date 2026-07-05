package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleLeagueLinkRepository extends JpaRepository<ArticleLeagueLink, Long> {

    List<ArticleLeagueLink> findByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);
}

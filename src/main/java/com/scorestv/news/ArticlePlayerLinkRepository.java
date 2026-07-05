package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticlePlayerLinkRepository extends JpaRepository<ArticlePlayerLink, Long> {

    List<ArticlePlayerLink> findByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);
}

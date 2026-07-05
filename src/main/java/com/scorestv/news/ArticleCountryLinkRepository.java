package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleCountryLinkRepository extends JpaRepository<ArticleCountryLink, Long> {

    List<ArticleCountryLink> findByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);
}

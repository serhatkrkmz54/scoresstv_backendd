package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleTeamLinkRepository extends JpaRepository<ArticleTeamLink, Long> {

    List<ArticleTeamLink> findByArticleId(Long articleId);

    void deleteByArticleId(Long articleId);
}

package com.scorestv.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Haber ile lig baglantisi. {@code leagueId} API-Football league id'sidir
 * (leagues.id'ye hard FK yok — bkz. V71 migration notu).
 */
@Entity
@Table(name = "article_league_links")
@Getter
@Setter
@NoArgsConstructor
public class ArticleLeagueLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    public ArticleLeagueLink(Long articleId, Long leagueId) {
        this.articleId = articleId;
        this.leagueId = leagueId;
    }
}

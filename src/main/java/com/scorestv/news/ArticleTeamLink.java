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
 * Haber ile takim baglantisi. {@code teamId} API-Football team id'sidir
 * (teams.id'ye hard FK yok — bkz. V71 migration notu).
 */
@Entity
@Table(name = "article_team_links")
@Getter
@Setter
@NoArgsConstructor
public class ArticleTeamLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    public ArticleTeamLink(Long articleId, Long teamId) {
        this.articleId = articleId;
        this.teamId = teamId;
    }
}

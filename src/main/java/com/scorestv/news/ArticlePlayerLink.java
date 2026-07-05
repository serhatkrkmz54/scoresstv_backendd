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
 * Haber ile oyuncu baglantisi. {@code playerId} API-Football player id'sidir
 * (players.id'ye hard FK yok — bkz. V71 migration notu).
 */
@Entity
@Table(name = "article_player_links")
@Getter
@Setter
@NoArgsConstructor
public class ArticlePlayerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    public ArticlePlayerLink(Long articleId, Long playerId) {
        this.articleId = articleId;
        this.playerId = playerId;
    }
}

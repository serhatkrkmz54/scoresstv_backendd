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
 * Haber ile mac (fixture) baglantisi. {@code fixtureId} API-Football fixture
 * id'sidir (fixtures.id'ye hard FK yok — bkz. V73 migration notu; diger link
 * tablolariyla ayni tercih).
 */
@Entity
@Table(name = "article_fixture_links")
@Getter
@Setter
@NoArgsConstructor
public class ArticleFixtureLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    public ArticleFixtureLink(Long articleId, Long fixtureId) {
        this.articleId = articleId;
        this.fixtureId = fixtureId;
    }
}

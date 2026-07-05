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
 * Haber ile ulke baglantisi. {@code countryId} countries.id'sidir
 * (hard FK yok — bkz. V71 migration notu).
 */
@Entity
@Table(name = "article_country_links")
@Getter
@Setter
@NoArgsConstructor
public class ArticleCountryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    public ArticleCountryLink(Long articleId, Long countryId) {
        this.articleId = articleId;
        this.countryId = countryId;
    }
}

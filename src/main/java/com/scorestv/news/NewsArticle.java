package com.scorestv.news;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Bir haber (news) — dil basina tek satir. TR ve EN versiyonlar
 * {@code translationGroupId} ile eslesir (null = tekil). Slug global benzersiz.
 *
 * <p>{@code body} sunucuda jsoup ile sanitize edilerek saklanir (stored-XSS
 * korunmasi). Soft-delete: {@code deletedAt} dolarsa kayit gizlenir, silinmez.
 */
@Entity
@Table(name = "news_articles")
@Getter
@Setter
@NoArgsConstructor
public class NewsArticle extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    /** Dil kodu: "tr" veya "en". */
    @Column(nullable = false, length = 2)
    private String lang;

    /** TR/EN esini baglayan grup id; null ise tekil (ceviri esi yok). */
    @Column(name = "translation_group_id")
    private Long translationGroupId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 600)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Kapak gorseli MinIO nesne anahtari (URL degil); null olabilir. */
    @Column(name = "cover_image_key", length = 255)
    private String coverImageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NewsStatus status = NewsStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NewsCategory category;

    @Column(name = "is_breaking", nullable = false)
    private boolean breaking = false;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    /** Web /haberler slider'inda gosterilsin mi (panelden yonetilir). */
    @Column(name = "in_slider", nullable = false)
    private boolean inSlider = false;

    /** Slider siralamasi — kucukten buyuge; esitse en yeni yayin once. */
    @Column(name = "slider_order", nullable = false)
    private int sliderOrder = 0;

    /** Spor kolu — "FOOTBALL" (varsayilan) / "BASKETBALL" / "VOLLEYBALL". */
    @Column(nullable = false, length = 16)
    private String sport = "FOOTBALL";

    /** Yazan kullanicinin id'si (users.id). */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** Kaynak — "MANUAL" (elle) veya harici bir kaynak adi. */
    @Column(nullable = false, length = 64)
    private String source = "MANUAL";

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    /** Harici kaynak makale kimligi (ice aktarma tekillestirmesi); MANUAL=null. */
    @Column(name = "external_id", length = 255)
    private String externalId;

    /** Yayin zamani; PUBLISHED oldugunda dolar, unpublish edilince temizlenir. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    /** Tahmini okuma suresi (dakika); body'den ~200 kelime/dk ile hesaplanir. */
    @Column(name = "reading_minutes")
    private Integer readingMinutes;

    /** Soft-delete zamani; null ise aktif. */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}

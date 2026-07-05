package com.scorestv.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Haber (news) sorgu katmani. Public sorgular yalniz PUBLISHED + silinmemis +
 * yayin zamani gecmis kayitlari doner; admin sorgular tum durumlari (silinmemis)
 * gorur.
 */
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    /** Public detay — yayinda, silinmemis, slug ile. */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.slug = :slug
              AND a.deletedAt IS NULL
            """)
    Optional<NewsArticle> findBySlugAndDeletedAtIsNull(@Param("slug") String slug);

    /** Admin detay — silinmemis, id ile (her durum). */
    Optional<NewsArticle> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Public liste — yayinda + silinmemis + yayin zamani <= now, dile gore,
     * opsiyonel kategori/spor/one-cikan filtreli. Filtre parametreleri null ise
     * o kritere bakilmaz. En yeni yayin once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
              AND (:category IS NULL OR a.category = :category)
              AND (:sport IS NULL OR a.sport = :sport)
              AND (:featured IS NULL OR a.featured = :featured)
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Page<NewsArticle> findPublished(@Param("lang") String lang,
                                    @Param("category") NewsCategory category,
                                    @Param("sport") String sport,
                                    @Param("featured") Boolean featured,
                                    @Param("now") Instant now,
                                    Pageable pageable);

    /**
     * Public liste — belirli bir takima bagli yayinda haberler (link tablosu
     * uzerinden). Dile gore, en yeni yayin once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.id IN (SELECT l.articleId FROM ArticleTeamLink l WHERE l.teamId = :teamId)
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Page<NewsArticle> findPublishedByTeam(@Param("teamId") Long teamId,
                                          @Param("lang") String lang,
                                          @Param("now") Instant now,
                                          Pageable pageable);

    /**
     * Public liste — belirli bir lige bagli yayinda haberler (link tablosu
     * uzerinden). Dile gore, en yeni yayin once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.id IN (SELECT l.articleId FROM ArticleLeagueLink l WHERE l.leagueId = :leagueId)
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Page<NewsArticle> findPublishedByLeague(@Param("leagueId") Long leagueId,
                                            @Param("lang") String lang,
                                            @Param("now") Instant now,
                                            Pageable pageable);

    /**
     * Admin liste — tum durumlar (silinmemis), opsiyonel durum/dil/kategori
     * filtreli + baslik/ozet metin aramasi (bos ise atlanir). En yeni once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND (:status IS NULL OR a.status = :status)
              AND (:lang IS NULL OR a.lang = :lang)
              AND (:category IS NULL OR a.category = :category)
              AND (:q IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                              OR LOWER(a.summary) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<NewsArticle> findForAdmin(@Param("status") NewsStatus status,
                                   @Param("lang") String lang,
                                   @Param("category") NewsCategory category,
                                   @Param("q") String q,
                                   Pageable pageable);

    /** Bir ceviri grubuna ait tum (silinmemis) haberler — dil esleri. */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.translationGroupId = :groupId
              AND a.deletedAt IS NULL
            """)
    List<NewsArticle> findByTranslationGroupId(@Param("groupId") Long groupId);

    /** Slug benzersizlik kontrolu (silinmis kayitlar dahil — slug global unique). */
    boolean existsBySlug(String slug);

    /** Detayda goruntuleme sayisini atomik artirir. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE NewsArticle a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);
}

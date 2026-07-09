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
     * Web slider'i — inSlider=true, YAYINDA, silinmemis, yayin zamani gecmis,
     * dile gore. slider_order artan; esitse en yeni yayin once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND a.inSlider = true
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
            ORDER BY a.sliderOrder ASC, a.publishedAt DESC, a.id DESC
            """)
    List<NewsArticle> findSlider(@Param("lang") String lang,
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
     * Public liste — belirli bir maca (fixture) bagli yayinda haberler (link
     * tablosu uzerinden). Dile gore, en yeni yayin once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.id IN (SELECT l.articleId FROM ArticleFixtureLink l WHERE l.fixtureId = :fixtureId)
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Page<NewsArticle> findPublishedByFixture(@Param("fixtureId") Long fixtureId,
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
              AND (:sport IS NULL OR a.sport = :sport)
              AND (:q IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                              OR LOWER(a.summary) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<NewsArticle> findForAdmin(@Param("status") NewsStatus status,
                                   @Param("lang") String lang,
                                   @Param("category") NewsCategory category,
                                   @Param("sport") String sport,
                                   @Param("q") String q,
                                   Pageable pageable);

    /**
     * DB fallback "ilgili haberler" (ES kapaliyken) — ayni dil, kendisi haric,
     * kaynak haberle en az bir takim VEYA lig linkini paylasan yayinda haberler.
     * En yeni yayin once. Paylasilan takim/lig id kumeleri (bos olabilir; ikisi
     * de bos ise sorgu hicbir sey dondurmez — cagiran son-care en yeniyi doner).
     */
    @Query("""
            SELECT DISTINCT a FROM NewsArticle a
            WHERE a.id <> :selfId
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.deletedAt IS NULL
              AND a.lang = :lang
              AND (a.publishedAt IS NULL OR a.publishedAt <= :now)
              AND (a.id IN (SELECT tl.articleId FROM ArticleTeamLink tl WHERE tl.teamId IN :teamIds)
                OR a.id IN (SELECT ll.articleId FROM ArticleLeagueLink ll WHERE ll.leagueId IN :leagueIds))
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    List<NewsArticle> findRelatedFallback(@Param("selfId") Long selfId,
                                          @Param("lang") String lang,
                                          @Param("teamIds") List<Long> teamIds,
                                          @Param("leagueIds") List<Long> leagueIds,
                                          @Param("now") Instant now,
                                          Pageable pageable);

    /** Bir ceviri grubuna ait tum (silinmemis) haberler — dil esleri. */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.translationGroupId = :groupId
              AND a.deletedAt IS NULL
            """)
    List<NewsArticle> findByTranslationGroupId(@Param("groupId") Long groupId);

    /**
     * Bir medya (gorsel) anahtarini KULLANAN silinmemis haberler — medya
     * kutuphanesinde silmeden once "hangi habere bagli" uyarisi icin.
     * Kapak: {@code coverImageKey} tam esitlik. Govde: gorselin URL'si key'i
     * ("articles/{uuid}.ext") icerdigi icin {@code body LIKE %key%}. En yeni once.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND (a.coverImageKey = :key OR a.body LIKE CONCAT('%', :key, '%'))
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    List<NewsArticle> findReferencingMedia(@Param("key") String key);

    /** Slug benzersizlik kontrolu (silinmis kayitlar dahil — slug global unique). */
    boolean existsBySlug(String slug);

    /** Detayda goruntuleme sayisini atomik artirir. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE NewsArticle a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // ---- Panel dashboard (admin ozet istatistikleri) ----

    /** Durum bazinda (silinmemis) haber sayilari: satir = [NewsStatus, Long]. */
    @Query("""
            SELECT a.status, COUNT(a) FROM NewsArticle a
            WHERE a.deletedAt IS NULL
            GROUP BY a.status
            """)
    List<Object[]> countGroupByStatus();

    /** Tum (silinmemis) haberlerin toplam goruntulenmesi. */
    @Query("SELECT COALESCE(SUM(a.viewCount), 0) FROM NewsArticle a WHERE a.deletedAt IS NULL")
    long sumViewCount();

    /** Belirli andan sonra yayinlanan (PUBLISHED) haber sayisi. */
    @Query("""
            SELECT COUNT(a) FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.publishedAt IS NOT NULL
              AND a.publishedAt >= :since
            """)
    long countPublishedSince(@Param("since") Instant since);

    /** En cok okunan yayinda haberler (viewCount azalan). Limit Pageable ile. */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
            ORDER BY a.viewCount DESC, a.publishedAt DESC
            """)
    List<NewsArticle> findTopViewed(Pageable pageable);

    /** Trend icin: belirli andan sonra yayinlanan haberlerin yayin zamanlari. */
    @Query("""
            SELECT a.publishedAt FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
              AND a.publishedAt IS NOT NULL
              AND a.publishedAt >= :since
            """)
    List<Instant> publishedAtSince(@Param("since") Instant since);

    /** Yazar bazinda toplam (silinmemis) haber sayilari: satir = [authorId, Long]. */
    @Query("""
            SELECT a.authorId, COUNT(a) FROM NewsArticle a
            WHERE a.deletedAt IS NULL
            GROUP BY a.authorId
            """)
    List<Object[]> countGroupByAuthor();

    /** Yazar bazinda YAYINDA haber sayilari: satir = [authorId, Long]. */
    @Query("""
            SELECT a.authorId, COUNT(a) FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND a.status = com.scorestv.news.NewsStatus.PUBLISHED
            GROUP BY a.authorId
            """)
    List<Object[]> countPublishedGroupByAuthor();

    /** Son dakika isaretli (silinmemis) haber sayisi. */
    long countByBreakingTrueAndDeletedAtIsNull();

    /** One cikan isaretli (silinmemis) haber sayisi. */
    long countByFeaturedTrueAndDeletedAtIsNull();

    // ---- Slider kuratorlugu (admin) ----

    /**
     * Admin slider listesi — dile gore, inSlider=true, silinmemis. (Public
     * findSlider'dan farki: publishedAt<=now kosulu YOK; zamanlanmis gelecek
     * tarihli slider ogeleri de kuratorlukte gorunur.) slider_order artan.
     */
    @Query("""
            SELECT a FROM NewsArticle a
            WHERE a.deletedAt IS NULL
              AND a.lang = :lang
              AND a.inSlider = true
            ORDER BY a.sliderOrder ASC, a.publishedAt DESC, a.id DESC
            """)
    List<NewsArticle> findSliderForAdmin(@Param("lang") String lang);

    /** Bir dildeki slider uyesi (silinmemis) haberler — kaydetmeden once temizlemek icin. */
    List<NewsArticle> findByLangAndInSliderTrueAndDeletedAtIsNull(String lang);
}

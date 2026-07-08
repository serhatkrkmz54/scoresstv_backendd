package com.scorestv.news;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.news.dto.CreateNewsRequest;
import com.scorestv.news.dto.MediaUsage;
import com.scorestv.news.dto.NewsDetail;
import com.scorestv.news.dto.NewsListItem;
import com.scorestv.news.dto.NewsPageResponse;
import com.scorestv.news.dto.UpdateNewsRequest;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Haber (news) servis katmani — olustur/guncelle/yayinla/sil + public liste ve
 * detay. Govde jsoup ile sanitize edilir, slug basliktan uretilir, okuma suresi
 * (~200 kelime/dk) hesaplanir, bagli varlik id'leri link tablolarina yazilir,
 * her mutasyon denetim gunlugune (news_audit_log) islenir.
 *
 * <p>Bagli varlik adlari mevcut team/league/country/player repository'leri ile
 * batch cozulur (N+1 onlenir). MinIO URL'leri MinioStorageService.publicUrl ile
 * uretilir.
 */
@Service
public class NewsService {

    /** Okuma suresi hesabi icin dakikada okunan kelime (yaklasik). */
    private static final int WORDS_PER_MINUTE = 200;
    private static final int MAX_PAGE_SIZE = 60;
    private static final int SLUG_MAX_ATTEMPTS = 50;

    private final NewsArticleRepository articleRepository;
    private final ArticleTeamLinkRepository teamLinkRepository;
    private final ArticleLeagueLinkRepository leagueLinkRepository;
    private final ArticleCountryLinkRepository countryLinkRepository;
    private final ArticlePlayerLinkRepository playerLinkRepository;
    private final ArticleFixtureLinkRepository fixtureLinkRepository;
    private final NewsAuditLogRepository auditLogRepository;
    private final NewsSanitizer sanitizer;
    private final MinioStorageService storage;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final NewsPushPublisher pushPublisher;
    /** ES kapaliyken (scorestv.elasticsearch.enabled=false) bean yoktur — opsiyonel. */
    private final ObjectProvider<NewsSearchPublisher> searchPublisher;
    /** ES kapaliyken bean yoktur — opsiyonel; DB fallback devreye girer. */
    private final ObjectProvider<NewsRelatedSearcher> relatedSearcher;

    public NewsService(NewsArticleRepository articleRepository,
                       ArticleTeamLinkRepository teamLinkRepository,
                       ArticleLeagueLinkRepository leagueLinkRepository,
                       ArticleCountryLinkRepository countryLinkRepository,
                       ArticlePlayerLinkRepository playerLinkRepository,
                       ArticleFixtureLinkRepository fixtureLinkRepository,
                       NewsAuditLogRepository auditLogRepository,
                       NewsSanitizer sanitizer,
                       MinioStorageService storage,
                       UserRepository userRepository,
                       TeamRepository teamRepository,
                       LeagueRepository leagueRepository,
                       CountryRepository countryRepository,
                       PlayerRepository playerRepository,
                       FixtureRepository fixtureRepository,
                       NewsPushPublisher pushPublisher,
                       ObjectProvider<NewsSearchPublisher> searchPublisher,
                       ObjectProvider<NewsRelatedSearcher> relatedSearcher) {
        this.articleRepository = articleRepository;
        this.teamLinkRepository = teamLinkRepository;
        this.leagueLinkRepository = leagueLinkRepository;
        this.countryLinkRepository = countryLinkRepository;
        this.playerLinkRepository = playerLinkRepository;
        this.fixtureLinkRepository = fixtureLinkRepository;
        this.auditLogRepository = auditLogRepository;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.playerRepository = playerRepository;
        this.fixtureRepository = fixtureRepository;
        this.pushPublisher = pushPublisher;
        this.searchPublisher = searchPublisher;
        this.relatedSearcher = relatedSearcher;
    }

    // ============================================================
    // Public sorgular
    // ============================================================

    /** Public liste — yayinda, dile gore, opsiyonel kategori/spor/one-cikan. */
    @Transactional(readOnly = true)
    public NewsPageResponse listPublished(String lang, int page, int size,
                                          NewsCategory category, String sport,
                                          Boolean featured) {
        Pageable pageable = safePage(page, size);
        Page<NewsArticle> result = articleRepository.findPublished(
                lang, category, sport, featured, Instant.now(), pageable);
        return toPageResponse(result);
    }

    /** Public liste — bir takima bagli yayinda haberler. */
    @Transactional(readOnly = true)
    public NewsPageResponse listPublishedByTeam(Long teamId, String lang,
                                                int page, int size) {
        Page<NewsArticle> result = articleRepository.findPublishedByTeam(
                teamId, lang, Instant.now(), safePage(page, size));
        return toPageResponse(result);
    }

    /** Public liste — bir lige bagli yayinda haberler. */
    @Transactional(readOnly = true)
    public NewsPageResponse listPublishedByLeague(Long leagueId, String lang,
                                                  int page, int size) {
        Page<NewsArticle> result = articleRepository.findPublishedByLeague(
                leagueId, lang, Instant.now(), safePage(page, size));
        return toPageResponse(result);
    }

    /** Public liste — bir maca (fixture) bagli yayinda haberler. */
    @Transactional(readOnly = true)
    public NewsPageResponse listPublishedByFixture(Long fixtureId, String lang,
                                                   int page, int size) {
        Page<NewsArticle> result = articleRepository.findPublishedByFixture(
                fixtureId, lang, Instant.now(), safePage(page, size));
        return toPageResponse(result);
    }

    /**
     * Public detay — yayinda (veya en azindan silinmemis) haber, slug ile.
     * Yayinda degilse 404 gibi davranir. Goruntuleme sayisini artirir.
     */
    @Transactional
    public NewsDetail getPublishedBySlug(String slug) {
        NewsArticle a = articleRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi."));
        if (a.getStatus() != NewsStatus.PUBLISHED
                || (a.getPublishedAt() != null && a.getPublishedAt().isAfter(Instant.now()))) {
            throw ApiException.notFound("Haber bulunamadi.");
        }
        articleRepository.incrementViewCount(a.getId());
        // Bulk UPDATE first-level cache'i tazelemez; yanittaki sayacin guncel
        // gorunmesi icin bellekteki entity'yi de artiriyoruz.
        a.setViewCount(a.getViewCount() + 1);
        return toDetail(a);
    }

    /**
     * "Ilgili haberler" — bir habere (slug) benzer, YAYINDA, ayni dil, kendisi
     * haric haberler. ES varsa {@link NewsRelatedSearcher} (paylasilan varlik +
     * more-like-this) kullanilir; ES kapali/bos ise DB fallback (paylasilan
     * takim/lig, en yeni). Sonuc {@link NewsListItem} listesi.
     */
    @Transactional(readOnly = true)
    public List<NewsListItem> listRelated(String slug, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        NewsArticle source = articleRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi."));
        if (source.getStatus() != NewsStatus.PUBLISHED
                || (source.getPublishedAt() != null
                    && source.getPublishedAt().isAfter(Instant.now()))) {
            throw ApiException.notFound("Haber bulunamadi.");
        }

        List<Long> teamIds = teamLinkRepository.findByArticleId(source.getId()).stream()
                .map(ArticleTeamLink::getTeamId).toList();
        List<Long> leagueIds = leagueLinkRepository.findByArticleId(source.getId()).stream()
                .map(ArticleLeagueLink::getLeagueId).toList();
        List<Long> fixtureIds = fixtureLinkRepository.findByArticleId(source.getId()).stream()
                .map(ArticleFixtureLink::getFixtureId).toList();

        // 1) ES yolu (varsa).
        NewsRelatedSearcher searcher = relatedSearcher.getIfAvailable();
        if (searcher != null) {
            com.scorestv.search.index.ArticleDoc doc =
                    new com.scorestv.search.index.ArticleDoc();
            doc.setId(source.getId());
            doc.setLang(source.getLang());
            doc.setTitle(source.getTitle());
            doc.setSummary(source.getSummary());
            doc.setTeams(teamIds);
            doc.setLeagues(leagueIds);
            doc.setFixtures(fixtureIds);
            List<Long> ids = searcher.findRelatedIds(doc, safeLimit);
            if (!ids.isEmpty()) {
                return loadPublishedInOrder(ids, source.getId(), safeLimit);
            }
        }

        // 2) DB fallback (ES kapali veya bos sonuc).
        if (teamIds.isEmpty() && leagueIds.isEmpty()) {
            return List.of();
        }
        List<Long> safeTeamIds = teamIds.isEmpty() ? List.of(-1L) : teamIds;
        List<Long> safeLeagueIds = leagueIds.isEmpty() ? List.of(-1L) : leagueIds;
        List<NewsArticle> rows = articleRepository.findRelatedFallback(
                source.getId(), source.getLang(), safeTeamIds, safeLeagueIds,
                Instant.now(), PageRequest.of(0, safeLimit));
        return rows.stream().map(this::toListItem).toList();
    }

    /**
     * ES'ten donen id sirasini koruyarak yayinda haberleri yukler + NewsListItem'a
     * cevirir (kendisi + yayinda olmayanlar elenir).
     */
    private List<NewsListItem> loadPublishedInOrder(List<Long> ids, Long selfId, int limit) {
        Instant now = Instant.now();
        Map<Long, NewsArticle> byId = articleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(NewsArticle::getId, Function.identity(), (a, b) -> a));
        List<NewsListItem> out = new java.util.ArrayList<>();
        for (Long id : ids) {
            if (id == null || id.equals(selfId)) continue;
            NewsArticle a = byId.get(id);
            if (a == null) continue;
            if (a.getStatus() != NewsStatus.PUBLISHED || a.getDeletedAt() != null) continue;
            if (a.getPublishedAt() != null && a.getPublishedAt().isAfter(now)) continue;
            out.add(toListItem(a));
            if (out.size() >= limit) break;
        }
        return out;
    }

    // ============================================================
    // Admin sorgular
    // ============================================================

    /** Admin liste — tum durumlar (silinmemis) + filtre + metin aramasi. */
    @Transactional(readOnly = true)
    public NewsPageResponse listForAdmin(NewsStatus status, String lang,
                                         NewsCategory category, String sport,
                                         String q, int page, int size) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String sportFilter = (sport == null || sport.isBlank()) ? null : sport.trim();
        Page<NewsArticle> result = articleRepository.findForAdmin(
                status, lang, category, sportFilter, query, safePage(page, size));
        return toPageResponse(result);
    }

    /** Admin detay — id ile (her durum, silinmemis). Goruntuleme artmaz. */
    @Transactional(readOnly = true)
    public NewsDetail getForAdmin(Long id) {
        NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi: " + id));
        return toDetail(a);
    }

    // ============================================================
    // Mutasyonlar
    // ============================================================

    /** Yeni haber olustur (EDITOR). body sanitize + slug + okuma suresi + linkler. */
    @Transactional
    public NewsDetail create(CreateNewsRequest req, Long authorId) {
        NewsArticle a = new NewsArticle();
        a.setLang(req.lang());
        a.setTranslationGroupId(req.translationGroupId());
        a.setTitle(req.title().trim());
        a.setSummary(trimOrNull(req.summary()));
        String sanitized = sanitizer.sanitizeBody(req.body());
        a.setBody(sanitized);
        a.setCoverImageKey(trimOrNull(req.coverImageKey()));
        a.setCategory(req.category());
        a.setSport(req.sport() != null && !req.sport().isBlank()
                ? req.sport().trim() : "FOOTBALL");
        a.setBreaking(req.isBreaking());
        a.setFeatured(req.isFeatured());
        a.setSource(req.source() != null && !req.source().isBlank()
                ? req.source().trim() : "MANUAL");
        a.setSourceUrl(trimOrNull(req.sourceUrl()));
        a.setAuthorId(authorId);
        a.setReadingMinutes(computeReadingMinutes(sanitized));
        a.setSlug(uniqueSlug(a.getTitle()));

        // Durum + yayin zamani.
        NewsStatus status = req.status() != null ? req.status() : NewsStatus.DRAFT;
        a.setStatus(status);
        if (status == NewsStatus.PUBLISHED) {
            a.setPublishedAt(req.publishedAt() != null ? req.publishedAt() : Instant.now());
        } else if (status == NewsStatus.SCHEDULED) {
            a.setPublishedAt(req.publishedAt());
        }

        a = articleRepository.save(a);
        replaceLinks(a.getId(), req.teamIds(), req.leagueIds(),
                req.countryIds(), req.playerIds(), req.fixtureIds());
        audit(a.getId(), authorId, "CREATE", "status=" + status);
        // PUBLISHED olarak olusturuldu + push istendi → commit sonrasi bildir.
        maybeTriggerPush(a, status, req.sendPush(), req.pushTarget());
        syncSearchIndex(a);
        return toDetail(a);
    }

    /** Haberi guncelle (EDITOR). body yeniden sanitize; linkler yeniden yazilir. */
    @Transactional
    public NewsDetail update(Long id, UpdateNewsRequest req, Long actorId) {
        NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi: " + id));

        boolean titleChanged = !a.getTitle().equals(req.title().trim());
        a.setLang(req.lang());
        a.setTranslationGroupId(req.translationGroupId());
        a.setTitle(req.title().trim());
        a.setSummary(trimOrNull(req.summary()));
        String sanitized = sanitizer.sanitizeBody(req.body());
        a.setBody(sanitized);
        // Kapak: istek coverImageKey=null gonderirse MEVCUT kapak KORUNUR.
        // (Edit formu key'i geri gonderemez; sadece yeni yukleme yeni key uretir.)
        // Bu sayede baska bir alan duzenlenince kapak yanlislikla silinmez.
        if (req.coverImageKey() != null) {
            a.setCoverImageKey(trimOrNull(req.coverImageKey()));
        }
        a.setCategory(req.category());
        a.setSport(req.sport() != null && !req.sport().isBlank()
                ? req.sport().trim() : a.getSport());
        a.setBreaking(req.isBreaking());
        a.setFeatured(req.isFeatured());
        a.setSource(req.source() != null && !req.source().isBlank()
                ? req.source().trim() : a.getSource());
        a.setSourceUrl(trimOrNull(req.sourceUrl()));
        a.setReadingMinutes(computeReadingMinutes(sanitized));

        // Baslik degistiyse slug'i tazele (yeni benzersiz slug uret).
        if (titleChanged) {
            a.setSlug(uniqueSlug(a.getTitle()));
        }

        // Durum gecisi (SCHEDULED/DRAFT/ARCHIVED). PUBLISHED'e gecis burada da
        // desteklenir; publish/unpublish ayrica dedike endpoint'lerdedir.
        if (req.status() != null) {
            applyStatus(a, req.status(), req.publishedAt());
        }

        a = articleRepository.save(a);
        replaceLinks(a.getId(), req.teamIds(), req.leagueIds(),
                req.countryIds(), req.playerIds(), req.fixtureIds());
        audit(a.getId(), actorId, "UPDATE", null);
        // Guncelleme PUBLISHED'e gecirdi + push istendi → commit sonrasi bildir.
        // (Idempotency: haber daha once push edilmisse NewsNotificationService atlar.)
        maybeTriggerPush(a, a.getStatus(), req.sendPush(), req.pushTarget());
        syncSearchIndex(a);
        return toDetail(a);
    }

    /**
     * Yayinla (EDITOR) — status=PUBLISHED, published_at set (yoksa now).
     *
     * <p>Publish endpoint'inin govdesi olmadigindan push niyeti opsiyonel query
     * parametreleriyle (sendPush/pushTarget) tasinir. sendPush true ise
     * yayinlama commit'inden SONRA push tetiklenir.
     */
    @Transactional
    public NewsDetail publish(Long id, Long actorId, Boolean sendPush,
                              NewsPushTarget pushTarget) {
        NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi: " + id));
        a.setStatus(NewsStatus.PUBLISHED);
        if (a.getPublishedAt() == null) {
            a.setPublishedAt(Instant.now());
        }
        a = articleRepository.save(a);
        audit(a.getId(), actorId, "PUBLISH", null);
        maybeTriggerPush(a, NewsStatus.PUBLISHED, sendPush, pushTarget);
        syncSearchIndex(a);
        return toDetail(a);
    }

    /** Yayindan kaldir (EDITOR) — status=DRAFT, published_at temizlenir. */
    @Transactional
    public NewsDetail unpublish(Long id, Long actorId) {
        NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi: " + id));
        a.setStatus(NewsStatus.DRAFT);
        a.setPublishedAt(null);
        a = articleRepository.save(a);
        audit(a.getId(), actorId, "UNPUBLISH", null);
        syncSearchIndex(a);
        return toDetail(a);
    }

    /** Soft-delete (yalniz ADMIN — controller gate eder). deleted_at set edilir. */
    @Transactional
    public void softDelete(Long id, Long actorId) {
        NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadi: " + id));
        a.setDeletedAt(Instant.now());
        articleRepository.save(a);
        audit(a.getId(), actorId, "DELETE", null);
        removeFromSearchIndex(a.getId());
    }

    // ============================================================
    // Yardimcilar
    // ============================================================

    /**
     * Haber PUBLISHED durumunda VE push istenmisse, commit sonrasi push tetikler.
     * Kapsam disinda (taslak/zamanlanmis ya da sendPush!=true) hicbir sey yapmaz.
     * Gercek gonderim {@link NewsPushPublisher} → {@link NewsNotificationService}
     * uzerinden {@code afterCommit} + {@code @Async} ile olur; bir push hatasi
     * bu (yayinlama) transaction'ini etkilemez.
     */
    private void maybeTriggerPush(NewsArticle a, NewsStatus status,
                                  Boolean sendPush, NewsPushTarget pushTarget) {
        if (status != NewsStatus.PUBLISHED) return;
        if (!Boolean.TRUE.equals(sendPush)) return;
        NewsPushTarget target = pushTarget != null ? pushTarget : NewsPushTarget.FAVORITES;
        pushPublisher.publishAfterCommit(a.getId(), target);
    }

    /**
     * Haberin Elasticsearch index'ini durumuna gore senkronlar (commit sonrasi):
     * PUBLISHED ise upsert, degilse index'ten cikarir. ES kapaliyken (bean yok)
     * no-op — {@link ObjectProvider} null-safe. Bir ES hatasi mutasyonu dusurmez.
     */
    private void syncSearchIndex(NewsArticle a) {
        NewsSearchPublisher p = searchPublisher.getIfAvailable();
        if (p == null) return;
        if (a.getStatus() == NewsStatus.PUBLISHED) {
            p.upsertAfterCommit(a.getId());
        } else {
            p.removeAfterCommit(a.getId());
        }
    }

    /** Haberi (commit sonrasi) ES index'inden cikarir (soft-delete). No-op if ES off. */
    private void removeFromSearchIndex(Long articleId) {
        NewsSearchPublisher p = searchPublisher.getIfAvailable();
        if (p != null) {
            p.removeAfterCommit(articleId);
        }
    }

    private void applyStatus(NewsArticle a, NewsStatus status, Instant publishedAt) {
        a.setStatus(status);
        switch (status) {
            case PUBLISHED -> a.setPublishedAt(
                    publishedAt != null ? publishedAt
                            : (a.getPublishedAt() != null ? a.getPublishedAt() : Instant.now()));
            case SCHEDULED -> a.setPublishedAt(publishedAt);
            case DRAFT, ARCHIVED -> a.setPublishedAt(null);
        }
    }

    /** Link tablolarini tamamen yeniden yazar (once sil, sonra ekle). */
    private void replaceLinks(Long articleId, List<Long> teamIds, List<Long> leagueIds,
                              List<Long> countryIds, List<Long> playerIds,
                              List<Long> fixtureIds) {
        teamLinkRepository.deleteByArticleId(articleId);
        leagueLinkRepository.deleteByArticleId(articleId);
        countryLinkRepository.deleteByArticleId(articleId);
        playerLinkRepository.deleteByArticleId(articleId);
        fixtureLinkRepository.deleteByArticleId(articleId);

        // ONEMLI: Silmeleri INSERT'lerden ONCE veritabanina gonder. Bu satir
        // olmadan Hibernate action-ordering'i (INSERT'ler DELETE'lerden once)
        // yuzunden ayni (article_id, entity_id) satiri hala DB'de iken tekrar
        // eklenmeye calisilir ve uq_article_team / uq_article_league ... ihlali
        // olur. flush() bu noktada YALNIZ bekleyen DELETE'leri yazar (henuz
        // link INSERT'i kuyruga girmedi); boylece cakisma kalkar. Ayni takim/
        // lig bagliyken haber guncellemesini de duzeltir.
        teamLinkRepository.flush();

        if (teamIds != null) {
            for (Long tid : distinct(teamIds)) {
                teamLinkRepository.save(new ArticleTeamLink(articleId, tid));
            }
        }
        if (leagueIds != null) {
            for (Long lid : distinct(leagueIds)) {
                leagueLinkRepository.save(new ArticleLeagueLink(articleId, lid));
            }
        }
        if (countryIds != null) {
            for (Long cid : distinct(countryIds)) {
                countryLinkRepository.save(new ArticleCountryLink(articleId, cid));
            }
        }
        if (playerIds != null) {
            for (Long pid : distinct(playerIds)) {
                playerLinkRepository.save(new ArticlePlayerLink(articleId, pid));
            }
        }
        if (fixtureIds != null) {
            for (Long fid : distinct(fixtureIds)) {
                fixtureLinkRepository.save(new ArticleFixtureLink(articleId, fid));
            }
        }
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private void audit(Long articleId, Long actorId, String action, String meta) {
        auditLogRepository.save(new NewsAuditLog(articleId, actorId, action, meta));
    }

    /** Baslik -> global benzersiz slug (cakisirsa -2, -3... eki). */
    private String uniqueSlug(String title) {
        String base = NewsSlugger.slugify(title);
        if (!articleRepository.existsBySlug(base)) {
            return base;
        }
        for (int i = 2; i < SLUG_MAX_ATTEMPTS; i++) {
            String candidate = base + "-" + i;
            if (!articleRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        // Son care: zaman damgasi ekle (pratikte buraya gelinmez).
        return base + "-" + System.currentTimeMillis();
    }

    /** ~200 kelime/dk uzerinden okuma suresi (en az 1 dk). */
    private int computeReadingMinutes(String sanitizedHtml) {
        String text = sanitizer.stripToText(sanitizedHtml);
        if (text.isBlank()) {
            return 1;
        }
        int words = text.trim().split("\\s+").length;
        return Math.max(1, (int) Math.ceil((double) words / WORDS_PER_MINUTE));
    }

    private Pageable safePage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(safePage, safeSize);
    }

    private NewsPageResponse toPageResponse(Page<NewsArticle> result) {
        List<NewsListItem> items = result.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new NewsPageResponse(items, result.getTotalElements(), result.hasNext());
    }

    private NewsListItem toListItem(NewsArticle a) {
        return new NewsListItem(
                a.getId(),
                a.getSlug(),
                a.getLang(),
                a.getTitle(),
                a.getSummary(),
                coverUrl(a.getCoverImageKey()),
                a.getCategory(),
                a.getSport(),
                a.isBreaking(),
                a.isFeatured(),
                a.getPublishedAt(),
                a.getReadingMinutes());
    }

    /**
     * Bir medya (gorsel) anahtarini kullanan silinmemis haberler — medya
     * kutuphanesinde silme oncesi "hangi habere bagli" gostermek icin.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<MediaUsage> mediaUsage(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        String k = key.trim();
        return articleRepository.findReferencingMedia(k).stream()
                .map(a -> new MediaUsage(
                        a.getId(),
                        a.getTitle(),
                        a.getSlug(),
                        a.getLang(),
                        a.getStatus() != null ? a.getStatus().name() : null,
                        k.equals(a.getCoverImageKey()),
                        a.getBody() != null && a.getBody().contains(k)))
                .toList();
    }

    /**
     * Medya (gorsel) nesnesini MinIO'dan siler. Cagiran (panel) bir habere
     * bagliysa kullaniciyi ONCEDEN uyarir; burada nesne dogrudan silinir.
     * Not: haber DB kaydi degismez (kapak URL'si kirilir) — bu kasitlidir.
     */
    public void deleteMedia(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("Silinecek gorsel anahtari (key) gerekli.");
        }
        try {
            storage.delete(key.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("Gorsel silinemedi: " + e.getMessage());
        }
    }

    private NewsDetail toDetail(NewsArticle a) {
        String authorName = userRepository.findById(a.getAuthorId())
                .map(User::getDisplayName)
                .orElse(null);

        List<Long> teamIds = teamLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleTeamLink::getTeamId).toList();
        List<Long> leagueIds = leagueLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleLeagueLink::getLeagueId).toList();
        List<Long> countryIds = countryLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleCountryLink::getCountryId).toList();
        List<Long> playerIds = playerLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticlePlayerLink::getPlayerId).toList();
        List<Long> fixtureIds = fixtureLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleFixtureLink::getFixtureId).toList();

        List<String> availableLangs;
        if (a.getTranslationGroupId() != null) {
            availableLangs = articleRepository
                    .findByTranslationGroupId(a.getTranslationGroupId()).stream()
                    .map(NewsArticle::getLang)
                    .distinct()
                    .toList();
        } else {
            availableLangs = List.of(a.getLang());
        }

        return new NewsDetail(
                a.getId(),
                a.getSlug(),
                a.getLang(),
                a.getTitle(),
                a.getSummary(),
                a.getBody(),
                coverUrl(a.getCoverImageKey()),
                a.getCoverImageKey(),
                a.getStatus(),
                a.getCategory(),
                a.getSport(),
                a.isBreaking(),
                a.isFeatured(),
                authorName,
                a.getViewCount(),
                a.getReadingMinutes(),
                a.getSource(),
                a.getSourceUrl(),
                a.getPublishedAt(),
                a.getTranslationGroupId(),
                availableLangs,
                resolveTeams(teamIds),
                resolveLeagues(leagueIds),
                resolveCountries(countryIds),
                resolvePlayers(playerIds),
                resolveFixtures(fixtureIds));
    }

    // --- Bagli varlik ad/gorsel cozumleme (batch, N+1 onlenir) ---

    private List<NewsDetail.EntityRef> resolveTeams(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> byId = teamRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(t -> new NewsDetail.EntityRef(t.getId(), t.getName(),
                        logoOf(t.getLogoKey(), t.getLogoUrl())))
                .toList();
    }

    private List<NewsDetail.EntityRef> resolveLeagues(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, League> byId = leagueRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(League::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(l -> new NewsDetail.EntityRef(l.getId(), l.getName(),
                        logoOf(l.getLogoKey(), l.getLogoUrl())))
                .toList();
    }

    private List<NewsDetail.EntityRef> resolveCountries(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Country> byId = countryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Country::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(c -> new NewsDetail.EntityRef(c.getId(), c.getName(),
                        logoOf(c.getFlagKey(), c.getFlagUrl())))
                .toList();
    }

    private List<NewsDetail.EntityRef> resolvePlayers(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Player> byId = playerRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(p -> new NewsDetail.EntityRef(p.getId(), p.getName(),
                        logoOf(p.getPhotoKey(), p.getPhotoUrl())))
                .toList();
    }

    /**
     * Bagli maclari (fixture) hafif referanslara cozer — batch (N+1 onlenir).
     * {@code findAllByIdWithDetails} lig + takimlar JOIN FETCH ile yuklendigi
     * icin lazy proxy'ye dokunulur; ad "Ev - Deplasman", logo ev takimi
     * logosu, kickoff mac baslangic zamani. Istenen id sirasi korunur.
     */
    private List<NewsDetail.FixtureRef> resolveFixtures(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Fixture> byId = fixtureRepository.findAllByIdWithDetails(ids).stream()
                .collect(Collectors.toMap(Fixture::getId, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(fx -> {
                    Team home = fx.getHomeTeam();
                    Team away = fx.getAwayTeam();
                    String homeName = home != null ? home.getName() : "?";
                    String awayName = away != null ? away.getName() : "?";
                    String logo = home != null
                            ? logoOf(home.getLogoKey(), home.getLogoUrl()) : null;
                    return new NewsDetail.FixtureRef(
                            fx.getId(), homeName + " - " + awayName, logo, fx.getKickoffAt());
                })
                .toList();
    }

    /** MinIO key (mirror) varsa CDN URL'si, yoksa orijinal harici URL. */
    private String logoOf(String key, String fallbackUrl) {
        if (key != null && !key.isBlank()) {
            return storage.publicUrl(key);
        }
        return fallbackUrl;
    }

    private String coverUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return storage.publicUrl(key);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

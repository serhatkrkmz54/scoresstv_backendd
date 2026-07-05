package com.scorestv.news;

import com.scorestv.search.index.ArticleDoc;
import com.scorestv.search.index.ArticleDocRepository;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Haberleri (news) Elasticsearch'e indexler — {@link ArticleDoc} yazar/siler.
 *
 * <p><b>ES-kosullu:</b> {@code scorestv.elasticsearch.enabled=false} ise bu
 * bean HIC yuklenmez (SearchIndexerService vb. ile ayni desen). Bu yuzden
 * {@link NewsService} bu servise <b>opsiyonel</b> baglanir (ObjectProvider) —
 * ES kapaliyken haber CRUD normal calisir, indexleme sessizce atlanir.
 *
 * <p><b>Ne indexlenir:</b> yalniz PUBLISHED + silinmemis + {@code publishedAt
 * <= now} haberler ARANABILIR. {@link #upsert} bu kosulu kontrol eder; kosul
 * saglanmiyorsa (taslak/zamanlanmis/arsiv/silinmis) index'ten CIKARIR (remove).
 *
 * <p><b>Hata izolasyonu:</b> her yazma try/catch + log; bir ES hatasi ASLA
 * haber mutasyonunu dusurmez. Tetikleme {@link NewsSearchPublisher} ile commit
 * SONRASI yapilir (push publisher ile ayni desen).
 *
 * <p><b>Indexleme deseni</b> {@code com.scorestv.search.indexer.SearchIndexerService}
 * (TeamDoc vb.) ile ayni: {@code ensureIndex} + repository.save/deleteById +
 * chunked reindex.
 */
@Service
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NewsIndexer {

    private static final Logger log = LoggerFactory.getLogger(NewsIndexer.class);
    private static final int CHUNK = 500;

    private final NewsArticleRepository articleRepository;
    private final ArticleTeamLinkRepository teamLinkRepository;
    private final ArticleLeagueLinkRepository leagueLinkRepository;
    private final ArticleCountryLinkRepository countryLinkRepository;
    private final ArticlePlayerLinkRepository playerLinkRepository;
    private final ArticleFixtureLinkRepository fixtureLinkRepository;
    private final NewsSanitizer sanitizer;
    private final MinioStorageService storage;
    private final ArticleDocRepository articleDocs;
    private final ElasticsearchOperations esOps;

    public NewsIndexer(NewsArticleRepository articleRepository,
                       ArticleTeamLinkRepository teamLinkRepository,
                       ArticleLeagueLinkRepository leagueLinkRepository,
                       ArticleCountryLinkRepository countryLinkRepository,
                       ArticlePlayerLinkRepository playerLinkRepository,
                       ArticleFixtureLinkRepository fixtureLinkRepository,
                       NewsSanitizer sanitizer,
                       MinioStorageService storage,
                       ArticleDocRepository articleDocs,
                       ElasticsearchOperations esOps) {
        this.articleRepository = articleRepository;
        this.teamLinkRepository = teamLinkRepository;
        this.leagueLinkRepository = leagueLinkRepository;
        this.countryLinkRepository = countryLinkRepository;
        this.playerLinkRepository = playerLinkRepository;
        this.fixtureLinkRepository = fixtureLinkRepository;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.articleDocs = articleDocs;
        this.esOps = esOps;
    }

    // ============================================================
    // Incremental — tek haber
    // ============================================================

    /**
     * Bir haberi (id ile taze okuyarak) index'e yazar veya (aranabilir degilse)
     * cikarir. Commit sonrasi ayri thread'de cagrildigi icin id ile yeniden
     * okur. ES hatasi loglanir, yutulur.
     */
    @Async
    @Transactional(readOnly = true)
    public void upsert(Long articleId) {
        if (articleId == null) return;
        try {
            NewsArticle a = articleRepository.findByIdAndDeletedAtIsNull(articleId)
                    .orElse(null);
            if (a == null || !isSearchable(a)) {
                remove(articleId);
                return;
            }
            ensureIndex();
            articleDocs.save(toDoc(a));
        } catch (Exception e) {
            log.warn("ES upsert(haber) fail id={}: {}", articleId, e.getMessage());
        }
    }

    /** Bir haberi index'ten siler. ES hatasi loglanir, yutulur. */
    @Async
    public void remove(Long articleId) {
        if (articleId == null) return;
        try {
            articleDocs.deleteById(articleId);
        } catch (Exception e) {
            log.warn("ES remove(haber) fail id={}: {}", articleId, e.getMessage());
        }
    }

    // ============================================================
    // Backfill — tum yayindaki haberler
    // ============================================================

    /**
     * Tum yayindaki (aranabilir) haberleri yeniden indexler. Admin endpoint'ten
     * cagrilir; idempotent (upsert). Chunked; sadece PUBLISHED + publishedAt<=now
     * dokumanlari yazar.
     *
     * @return indexlenen dokuman sayisi
     */
    @Transactional(readOnly = true)
    public long reindexAll() {
        log.info("ES reindex: haberler baslatildi");
        ensureIndex();
        Instant now = Instant.now();
        long total = 0;
        int page = 0;
        while (true) {
            var slice = articleRepository.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<ArticleDoc> batch = new ArrayList<>(slice.getSize());
            for (NewsArticle a : slice) {
                if (a.getDeletedAt() == null && isSearchable(a, now)) {
                    batch.add(toDoc(a));
                }
            }
            if (!batch.isEmpty()) {
                articleDocs.saveAll(batch);
                total += batch.size();
            }
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: haberler tamamlandi, toplam={}", total);
        return total;
    }

    // ============================================================
    // Yardimcilar
    // ============================================================

    private boolean isSearchable(NewsArticle a) {
        return isSearchable(a, Instant.now());
    }

    private boolean isSearchable(NewsArticle a, Instant now) {
        return a.getStatus() == NewsStatus.PUBLISHED
                && a.getDeletedAt() == null
                && (a.getPublishedAt() == null || !a.getPublishedAt().isAfter(now));
    }

    private void ensureIndex() {
        try {
            IndexOperations ops = esOps.indexOps(ArticleDoc.class);
            if (!ops.exists()) {
                ops.create();
                ops.putMapping(ops.createMapping(ArticleDoc.class));
                log.info("ES index olusturuldu: {}", ArticleDoc.class.getSimpleName());
            }
        } catch (Exception e) {
            log.warn("ES ensureIndex hata ({}): {}",
                    ArticleDoc.class.getSimpleName(), e.getMessage());
        }
    }

    private ArticleDoc toDoc(NewsArticle a) {
        ArticleDoc d = new ArticleDoc();
        d.setId(a.getId());
        d.setSlug(a.getSlug());
        d.setLang(a.getLang());
        d.setTitle(a.getTitle());
        d.setSummary(a.getSummary());
        d.setBodyText(sanitizer.stripToText(a.getBody()));
        d.setTeams(teamLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleTeamLink::getTeamId).toList());
        d.setLeagues(leagueLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleLeagueLink::getLeagueId).toList());
        d.setCountries(countryLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleCountryLink::getCountryId).toList());
        d.setPlayers(playerLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticlePlayerLink::getPlayerId).toList());
        d.setFixtures(fixtureLinkRepository.findByArticleId(a.getId()).stream()
                .map(ArticleFixtureLink::getFixtureId).toList());
        d.setSport(a.getSport());
        d.setCategory(a.getCategory() != null ? a.getCategory().name() : null);
        d.setBreaking(a.isBreaking());
        d.setFeatured(a.isFeatured());
        d.setPublishedAt(a.getPublishedAt());
        // Populerlik viewCount'tan; SearchService function_score Log1p uygular
        // (ham sayi guvenli — 0 da olur).
        d.setPopularity((double) a.getViewCount());
        d.setCoverImageUrl(coverUrl(a.getCoverImageKey()));
        return d;
    }

    private String coverUrl(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return storage.publicUrl(key);
        } catch (Exception e) {
            log.debug("Haber kapak URL cozulemedi key={}: {}", key, e.getMessage());
            return null;
        }
    }
}

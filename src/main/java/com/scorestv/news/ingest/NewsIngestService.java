package com.scorestv.news.ingest;

import com.scorestv.news.NewsArticleRepository;
import com.scorestv.news.NewsService;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Haber içe aktarma orkestratörü (agregatör): NewsData.io'dan çeker →
 * tekilleştirir → kapak görselini MinIO'ya aynalar → {@link NewsService} ile
 * <b>DRAFT</b> haber açar. Editör addnews panelinde onaylayıp yayınlar.
 *
 * <p>Tam makale metni SAKLANMAZ (telif) — yalnız başlık + özet + kaynağa link.
 * {@code enabled=false} veya apiKey/authorId eksikse hiçbir şey yapmaz.
 */
@Service
public class NewsIngestService {

    private static final Logger log = LoggerFactory.getLogger(NewsIngestService.class);

    /** Kapak görseli üst sınırı (aynalama). */
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final int TITLE_MAX = 255;
    private static final int SUMMARY_MAX = 600;
    private static final int SOURCE_URL_MAX = 1024;

    private final NewsDataClient client;
    private final NewsIngestProperties props;
    private final NewsService newsService;
    private final NewsArticleRepository articleRepository;
    private final MinioStorageService storage;
    private final RestClient imageClient;

    public NewsIngestService(NewsDataClient client,
                             NewsIngestProperties props,
                             NewsService newsService,
                             NewsArticleRepository articleRepository,
                             MinioStorageService storage) {
        this.client = client;
        this.props = props;
        this.newsService = newsService;
        this.articleRepository = articleRepository;
        this.storage = storage;

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.imageClient = RestClient.builder().requestFactory(rf).build();
    }

    /**
     * Bir çekim döngüsü: kaynaktan güncel haberleri al, yeni (tekil) olanları
     * DRAFT olarak aç. Açılan DRAFT sayısını döner. Guard (kapalı/eksik config)
     * durumunda 0.
     */
    public int runOnce() {
        if (!props.enabled()) {
            return 0;
        }
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            log.warn("Haber içe aktarma açık ama NEWSDATA_API_KEY yok — atlanıyor.");
            return 0;
        }
        if (props.authorId() == null || props.authorId() <= 0) {
            log.warn("Haber içe aktarma açık ama NEWS_INGEST_AUTHOR_ID yok — DRAFT hangi "
                    + "kullanıcı adına açılacağı belirsiz, atlanıyor.");
            return 0;
        }

        int created = 0;
        for (NewsIngestProperties.Feed feed : effectiveFeeds()) {
            created += ingestFeed(feed);
        }
        if (created > 0) {
            log.info("Haber içe aktarma: {} yeni DRAFT açıldı (kaynak={}).",
                    created, props.source());
        }
        return created;
    }

    /** Feed listesi; yapılandırılmamışsa güvenli varsayılan (yalnız TR). */
    private List<NewsIngestProperties.Feed> effectiveFeeds() {
        if (props.feeds() != null && !props.feeds().isEmpty()) {
            return props.feeds();
        }
        return List.of(new NewsIngestProperties.Feed("tr", "tr", "tr"));
    }

    /** Bir dil feed'ini işler (feed başına maxPerRun); açılan DRAFT sayısını döner. */
    private int ingestFeed(NewsIngestProperties.Feed feed) {
        final String lang = (feed.lang() != null && !feed.lang().isBlank())
                ? feed.lang().trim() : "tr";
        final List<NewsDataDtos.Article> items =
                client.fetchLatest(feed.language(), feed.country());
        int created = 0;
        for (NewsDataDtos.Article a : items) {
            if (created >= props.maxPerRun()) {
                break;
            }
            try {
                if (ingestOne(a, lang)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                // Tek bir haber hatası tüm döngüyü bozmasın.
                log.warn("Haber içe aktarma tekil hata (id={}): {}",
                        a.articleId(), ex.toString());
            }
        }
        return created;
    }

    /** Tek bir haberi DRAFT olarak açar (verilen dilde). Zaten varsa/geçersizse false. */
    private boolean ingestOne(NewsDataDtos.Article a, String lang) {
        final String externalId = a.articleId();
        final String title = a.title();
        if (externalId == null || externalId.isBlank()
                || title == null || title.isBlank()) {
            return false;
        }
        // Tekilleştirme — daha önce içe aktarıldıysa (DRAFT/yayında) atla.
        if (articleRepository.existsBySourceAndExternalId(props.source(), externalId)) {
            return false;
        }

        final String desc = a.description() == null ? "" : a.description().trim();
        final String link = a.link();
        final String srcName = (a.sourceName() != null && !a.sourceName().isBlank())
                ? a.sourceName() : props.source();

        // Kapak görselini aynala (başarısızsa kapaksız devam).
        final String coverKey = mirrorImage(a.imageUrl());

        // Gövde: özet + kaynağa link (tam metin YOK — telif). Metin HTML-escape.
        final StringBuilder body = new StringBuilder();
        body.append("<p>").append(escape(desc.isBlank() ? title : desc)).append("</p>");
        if (isHttpUrl(link)) {
            body.append("<p><a href=\"").append(escape(link))
                    .append("\" target=\"_blank\" rel=\"noopener nofollow\">")
                    .append("Haberin kaynağı: ").append(escape(srcName))
                    .append("</a></p>");
        }

        newsService.ingestExternalDraft(
                lang,
                clip(title, TITLE_MAX),
                desc.isBlank() ? null : clip(desc, SUMMARY_MAX),
                body.toString(),
                coverKey,
                props.source(),
                isHttpUrl(link) ? clip(link, SOURCE_URL_MAX) : null,
                externalId,
                props.authorId());
        return true;
    }

    /**
     * Harici görsel URL'sini indirip MinIO'ya yükler; nesne anahtarını döner.
     * Başarısız/uygunsuz (image olmayan, çok büyük) ise null → kapaksız haber.
     */
    private String mirrorImage(String url) {
        if (!isHttpUrl(url)) {
            return null;
        }
        try {
            ResponseEntity<byte[]> resp = imageClient.get()
                    .uri(java.net.URI.create(url)).retrieve().toEntity(byte[].class);
            byte[] data = resp.getBody();
            if (data == null || data.length == 0 || data.length > MAX_IMAGE_BYTES) {
                return null;
            }
            MediaType ct = resp.getHeaders().getContentType();
            if (ct == null || !"image".equalsIgnoreCase(ct.getType())) {
                return null;
            }
            String ext = switch (ct.getSubtype().toLowerCase()) {
                case "jpeg", "jpg" -> "jpg";
                case "png" -> "png";
                case "webp" -> "webp";
                case "gif" -> "gif";
                default -> "jpg";
            };
            // Editör yüklemeleriyle AYNI prefix ("articles/") — böylece panel medya
            // kütüphanesi (storage.list("articles/")) içe aktarılan kapakları da gösterir.
            String key = "articles/" + UUID.randomUUID() + "." + ext;
            storage.upload(key, data, ct.toString());
            return key;
        } catch (Exception e) {
            log.debug("Kapak görseli aynalanamadı ({}): {}", url, e.toString());
            return null;
        }
    }

    private static boolean isHttpUrl(String s) {
        return s != null && (s.startsWith("http://") || s.startsWith("https://"));
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Basit HTML-escape (metni güvenli metin olarak gömmek için). */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

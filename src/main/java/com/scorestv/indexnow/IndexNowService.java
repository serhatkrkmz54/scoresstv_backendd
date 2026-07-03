package com.scorestv.indexnow;

import com.scorestv.football.seo.SeoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IndexNow (Bing/Yandex) gönderim istemcisi.
 *
 * <p>Verilen tam (absolute) URL listesini {@code POST https://api.indexnow.org/indexnow}
 * ucuna JSON gövdeyle iletir. Tek istekte en fazla {@value #MAX_URLS_PER_REQUEST}
 * URL — daha büyük listeler parçalara bölünür. {@code enabled=false}, anahtar boş
 * ya da liste boş ise no-op (debug log). Hata durumunda {@code warn} loglanır ve
 * yutulur — bu servis bir job içinden çağrılır, exception job'u düşürmemeli.
 *
 * <p>{@code host} ve {@code keyLocation} {@link SeoProperties#siteUrl()}'den
 * türetilir; canonical URL'ler zaten aynı site adresiyle kuruluyor
 * ({@link com.scorestv.football.seo.MatchDetailSeoBuilder}).
 */
@Service
public class IndexNowService {

    private static final Logger log = LoggerFactory.getLogger(IndexNowService.class);

    private static final String ENDPOINT = "https://api.indexnow.org/indexnow";

    /** IndexNow tek istek üst sınırı. */
    private static final int MAX_URLS_PER_REQUEST = 10_000;

    private final RestClient http;
    private final IndexNowProperties props;
    private final SeoProperties seoProperties;

    public IndexNowService(IndexNowProperties props, SeoProperties seoProperties) {
        this.props = props;
        this.seoProperties = seoProperties;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder()
                .baseUrl(ENDPOINT)
                .requestFactory(rf)
                .build();
    }

    /**
     * Verilen (tam) URL'leri IndexNow'a gönderir. Kapalı / anahtar boş / liste
     * boş ise hiçbir şey yapmaz. 10k'lık parçalara bölerek her parçayı ayrı POST
     * eder; hata parça bazında yutulur.
     *
     * @param urls gönderilecek absolute URL'ler (canonical)
     */
    public void submit(List<String> urls) {
        if (!props.enabled()) {
            log.debug("IndexNow kapalı — {} URL gönderilmedi.", urls == null ? 0 : urls.size());
            return;
        }
        if (props.key() == null || props.key().isBlank()) {
            log.debug("IndexNow anahtarı boş — gönderim atlandı.");
            return;
        }
        if (urls == null || urls.isEmpty()) {
            log.debug("IndexNow gönderilecek URL yok.");
            return;
        }

        String siteUrl = trimTrailingSlash(seoProperties.siteUrl());
        String host = resolveHost(siteUrl);
        if (host == null) {
            log.warn("IndexNow host çözülemedi (siteUrl={}), gönderim atlandı.", siteUrl);
            return;
        }
        String keyLocation = siteUrl + "/" + props.key() + ".txt";

        for (int from = 0; from < urls.size(); from += MAX_URLS_PER_REQUEST) {
            int to = Math.min(from + MAX_URLS_PER_REQUEST, urls.size());
            List<String> chunk = urls.subList(from, to);
            postChunk(host, keyLocation, chunk);
        }
    }

    /** Tek parçayı POST eder; ağ/HTTP hatası warn loglanıp yutulur. */
    private void postChunk(String host, String keyLocation, List<String> chunk) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", host);
        body.put("key", props.key());
        body.put("keyLocation", keyLocation);
        body.put("urlList", chunk);
        try {
            http.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("IndexNow: {} URL gönderildi (host={}).", chunk.size(), host);
        } catch (Exception ex) {
            log.warn("IndexNow gönderimi başarısız ({} URL, host={}): {}",
                    chunk.size(), host, ex.toString());
        }
    }

    /** siteUrl'den host kısmını çıkarır; parse edilemezse null. */
    private static String resolveHost(String siteUrl) {
        try {
            String host = URI.create(siteUrl).getHost();
            return (host == null || host.isBlank()) ? null : host;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

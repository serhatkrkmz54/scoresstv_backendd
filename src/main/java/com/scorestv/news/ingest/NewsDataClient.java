package com.scorestv.news.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * NewsData.io HTTP istemcisi. Kimlik {@code apikey} query parametresiyle geçer
 * (header değil). Hata/ağ/kota sorununda boş liste döner — çağıran (ingest
 * servisi) sessizce atlar. 429 (kota) sonrası kısa cooldown ile hammer'lamayı
 * ve boşuna kota yakmayı önler.
 *
 * <p>{@code HighlightlyClient} ile aynı desen: RestClient + timeout + defensive
 * try/catch.
 */
@Component
public class NewsDataClient {

    private static final Logger log = LoggerFactory.getLogger(NewsDataClient.class);

    private final RestClient http;
    private final NewsIngestProperties props;
    private final boolean keyConfigured;

    /** 429 sonrası çağrıları durduracak an (epoch ms) — boşuna kota yakma. */
    private volatile long breachedUntilMs = 0L;

    public NewsDataClient(NewsIngestProperties props) {
        this.props = props;
        this.keyConfigured = props.apiKey() != null && !props.apiKey().isBlank();

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(rf)
                .build();
        if (!keyConfigured) {
            log.warn("NewsData apiKey tanımlı değil — haber içe aktarma çağrıları boş döner.");
        }
    }

    /**
     * {@code GET /latest?apikey&language&category[&country]} — verilen dil (ve
     * varsa ülke) icin en güncel spor haberleri. country boş ise gönderilmez
     * (uluslararası). Hata/kota durumunda boş liste.
     */
    public List<NewsDataDtos.Article> fetchLatest(String language, String country) {
        if (!keyConfigured || System.currentTimeMillis() < breachedUntilMs) {
            return List.of();
        }
        try {
            NewsDataDtos.Response resp = http.get()
                    .uri(u -> {
                        u.path("/latest")
                                .queryParam("apikey", props.apiKey())
                                .queryParam("language", language)
                                .queryParam("category", props.category());
                        if (country != null && !country.isBlank()) {
                            u.queryParam("country", country);
                        }
                        return u.build();
                    })
                    .retrieve()
                    .body(NewsDataDtos.Response.class);
            if (resp == null || resp.results() == null) {
                return List.of();
            }
            return resp.results();
        } catch (HttpClientErrorException.TooManyRequests e) {
            // Günlük/anlık kota — 30 dk çağrı durdur (free plan günlük 200 kredi).
            breachedUntilMs = System.currentTimeMillis() + 30 * 60_000L;
            log.warn("NewsData 429 (kota) — 30 dk içe aktarma durduruldu.");
            return List.of();
        } catch (Exception e) {
            log.warn("NewsData fetch hatası: {}", e.toString());
            return List.of();
        }
    }
}

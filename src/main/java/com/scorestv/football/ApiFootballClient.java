package com.scorestv.football;

import com.scorestv.config.ScorestvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * API-Football v3 (api-sports.io) için tek giriş noktası olan HTTP istemcisi.
 *
 * <p>Tüm endpoint servisleri bu sınıfın {@link #get} metodunu kullanır; böylece
 * kimlik doğrulama, zaman aşımı, <b>dakikalık hız sınırlama</b>, kota loglama ve
 * hata yönetimi tek yerde toplanır.
 *
 * <p>Hız sınırlama burada merkezîdir: tüm istekler {@code requests-per-minute}
 * ayarına göre eşit aralıklarla seri hale getirilir, dolayısıyla hiçbir senkron
 * dakikalık API limitini aşamaz.
 *
 * <p>API yalnızca GET isteklerini ve tek bir kimlik header'ini ({@code x-apisports-key})
 * kabul eder; istemci bu kurala uyacak şekilde sade tutulmuştur.
 */
@Component
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballClient.class);

    /** API-Football'un kabul ettiği tek kimlik doğrulama header'i. */
    private static final String API_KEY_HEADER = "x-apisports-key";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final boolean keyConfigured;
    private final ApiQuotaTracker quotaTracker;

    /** İki istek arasındaki en az süre (ms) — dakikalık limiti aşmamak için. */
    private final long minIntervalMs;
    /** Son isteğin gönderildiği an (epoch ms); {@link #throttle()} tarafından korunur. */
    private long lastRequestAt;

    public ApiFootballClient(ScorestvProperties properties,
                             ApiQuotaTracker quotaTracker) {
        this.quotaTracker = quotaTracker;
        ScorestvProperties.ApiFootball cfg = properties.apiFootball();
        this.keyConfigured = cfg.key() != null && !cfg.key().isBlank();

        int perMinute = Math.max(1, cfg.requestsPerMinute());
        this.minIntervalMs = 60_000L / perMinute;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .requestFactory(requestFactory);
        if (keyConfigured) {
            builder.defaultHeader(API_KEY_HEADER, cfg.key());
        }
        this.restClient = builder.build();

        if (keyConfigured) {
            log.info("ApiFootballClient hazır. baseUrl={} hız sınırı={} istek/dk (~{} ms aralık)",
                    cfg.baseUrl(), perMinute, minIntervalMs);
        } else {
            log.warn("API-Football anahtarı tanımlı değil (API_FOOTBALL_KEY boş). "
                    + "Futbol verisi çağrıları devre dışı; .env dosyasına anahtarı ekleyin.");
        }
    }

    /**
     * API-Football'da bir endpoint'i GET ile çağırır ve ortak yanıt zarfını döner.
     *
     * <p>Çağrı, dakikalık limiti aşmamak için {@link #throttle()} ile geciktirilebilir.
     *
     * @param path        endpoint yolu, başında "/" ile (örn. "/status", "/leagues")
     * @param queryParams sorgu parametreleri; boş olabilir ({@code Map.of()}).
     *                    Değeri {@code null} olan parametreler atlanır.
     * @param typeRef     beklenen yanıt tipi
     * @param <T>         {@code response} alanının tipi
     * @return başarılı yanıt zarfı (hata içermez)
     * @throws ApiFootballException yapılandırma, bağlantı, kota veya sağlayıcı hatasında
     */
    public <T> ApiFootballResponse<T> get(String path,
                                          Map<String, ?> queryParams,
                                          ParameterizedTypeReference<ApiFootballResponse<T>> typeRef) {
        if (!keyConfigured) {
            throw ApiFootballException.notConfigured(
                    "Futbol veri sağlayıcısı yapılandırılmamış.");
        }
        throttle();

        ResponseEntity<ApiFootballResponse<T>> entity;
        try {
            entity = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        if (queryParams != null) {
                            queryParams.forEach((key, value) -> {
                                if (value != null) {
                                    uriBuilder.queryParam(key, value);
                                }
                            });
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .toEntity(typeRef);
        } catch (RestClientException ex) {
            log.error("API-Football çağrısı başarısız: path={} hata={}", path, ex.getMessage());
            throw ApiFootballException.upstream(
                    "Futbol veri sağlayıcısına ulaşılamadı.");
        }

        logQuota(path, entity.getHeaders());

        ApiFootballResponse<T> body = entity.getBody();
        if (body == null) {
            throw ApiFootballException.upstream(
                    "Futbol veri sağlayıcısından boş yanıt alındı.");
        }
        if (body.hasErrors()) {
            throw classifyError(path, body.errorText());
        }
        return body;
    }

    /**
     * İstekleri dakikalık limite göre eşit aralıklarla seri hale getirir.
     *
     * <p>{@code synchronized}: eşzamanlı çağıranlar (başlangıç senkronu + admin
     * tetiklemesi aynı anda) tek küresel sıraya girer; böylece toplam hız hiçbir
     * koşulda {@code requests-per-minute}'ü aşmaz.
     */
    private synchronized void throttle() {
        long waitMs = (lastRequestAt + minIntervalMs) - System.currentTimeMillis();
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    /**
     * Yanıt header'larındaki kalan kota bilgisini hem {@link ApiQuotaTracker}'a
     * push eder hem de loglar. Tracker degerleri merkezi karar mekanizmasinda
     * (SyncQueueWorker adaptif yavaslama, lazy sync atlamasi) kullanilir.
     */
    private void logQuota(String path, HttpHeaders headers) {
        quotaTracker.updateFromHeaders(headers);
        String dailyRemaining = headers.getFirst("x-ratelimit-requests-remaining");
        if (dailyRemaining == null) {
            return;
        }
        log.info("API-Football kota [{}]: günlük kalan {}/{}, dakikalık kalan {}/{}",
                path,
                dailyRemaining,
                headers.getFirst("x-ratelimit-requests-limit"),
                headers.getFirst("X-RateLimit-Remaining"),
                headers.getFirst("X-RateLimit-Limit"));
    }

    /** {@code errors} alanı dolu geldiğinde uygun istisnayı üretir. */
    private ApiFootballException classifyError(String path, String detail) {
        log.warn("API-Football hata döndürdü: path={} errors={}", path, detail);
        String lower = detail.toLowerCase();
        if (lower.contains("limit") || lower.contains("quota") || lower.contains("rate")) {
            return ApiFootballException.quotaExceeded(
                    "Futbol veri sağlayıcısının istek kotası doldu. Lütfen daha sonra tekrar deneyin.");
        }
        return ApiFootballException.upstream(
                "Futbol veri sağlayıcısı isteği reddetti.");
    }
}

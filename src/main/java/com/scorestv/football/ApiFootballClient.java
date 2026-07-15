package com.scorestv.football;

import com.scorestv.config.ScorestvProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * API-Football v3 (api-sports.io) icin tek giris noktasi olan HTTP istemcisi.
 *
 * <p>Tum endpoint servisleri bu sinifin {@link #get} metodunu kullanir; boylece
 * kimlik dogrulama, zaman asimi, <b>oncelikli saniye-bazli hiz sinirlama</b>,
 * kota loglama ve hata yonetimi tek yerde toplanir.
 *
 * <h2>Hiz sinirlama mimarisi (token bucket)</h2>
 * <p>API-Football'un Custom plani dakika kotasi gevsek (1200/dk), ancak
 * <b>saniye-bazli burst guard</b> sikti — 1 saniyede 15+ paralel istek
 * "Too many requests" hatasi tetikler. Bu yuzden istemci iki ayri kova
 * tutar:
 * <ul>
 *   <li>{@link RequestPriority#LIVE} — kullanici-bekleyen veriler. Canli skor
 *       guncellemesi, mac detay tab'lari, anasayfa fixture listesi. Saniyede
 *       {@code liveTokensPerSecond} adet rezerv slot; lazy yoguntundan
 *       etkilenmez.</li>
 *   <li>{@link RequestPriority#LAZY} — arka plan senkronu. Squad, transfer,
 *       trophy, sidelined, coach, takim/lig refresh. Saniyede
 *       {@code lazyTokensPerSecond} adet; burst'te sirada bekler.</li>
 * </ul>
 *
 * <p>Kovalar her saniye {@link ScheduledExecutorService} ile yeniden dolar.
 * Bos kovaya dusen istek bir sonraki saniye refill'ini bekler (en kotu durumda
 * ~1 sn gecikme — token bucket dogal davranis).
 *
 * <h2>Path-based priority detection</h2>
 * <p>Cagiranlar oncelik belirlememeli — istemci {@link #detectPriority(String)}
 * ile path'ten otomatik karar verir. {@code /fixtures*} (canli/mac detay
 * verisi) LIVE, diger her yol LAZY. Ozel durum gerekiyorsa
 * {@link #get(String, Map, ParameterizedTypeReference, RequestPriority)}
 * overload'i kullanilir.
 *
 * <p>API yalnizca GET isteklerini ve tek bir kimlik header'ini
 * ({@code x-apisports-key}) kabul eder; istemci bu kurala uyacak sekilde sade
 * tutulmustur.
 */
@Component
public class ApiFootballClient {

    private static final Logger log = LoggerFactory.getLogger(ApiFootballClient.class);

    /** API-Football'un kabul ettigi tek kimlik dogrulama header'i. */
    private static final String API_KEY_HEADER = "x-apisports-key";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    /** Retry-After header'i gelmezse 429 sonrasi uygulanacak varsayilan cooldown. */
    private static final Duration DEFAULT_429_COOLDOWN = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final boolean keyConfigured;
    private final ApiQuotaTracker quotaTracker;

    /** LIVE oncelikli istekler icin saniye-bazli kova (kullanici-bekleyen). */
    private final Semaphore liveBucket;
    /** LAZY oncelikli istekler icin saniye-bazli kova (arka plan sync). */
    private final Semaphore lazyBucket;
    /** Saniyede LIVE icin maks token sayisi (refill kapasitesi). */
    private final int liveTokensPerSecond;
    /** Saniyede LAZY icin maks token sayisi (refill kapasitesi). */
    private final int lazyTokensPerSecond;
    /** Kovalari her saniye yeniden dolduran arka plan ileti. */
    private final ScheduledExecutorService bucketRefiller;

    /**
     * 429/kota sonrası LOKAL cooldown bitiş epoch'u (ms). {@link #refillSingle}
     * bu süre boyunca token bucket'ları DOLDURMAZ — böylece cooldown bitiminde
     * dolu kova bir anda boşalıp yeni 429'u tetiklemez (thundering herd önlenir).
     */
    private volatile long localCooldownUntilMs = 0L;

    /**
     * API-Football endpoint'lerine yapilan isteklerin onceligi.
     * <ul>
     *   <li>{@link #LIVE}: kullanici-bekleyen veriler (canli skor, mac detay,
     *       anasayfa fixture). Garantili rezerv slot'a sahip; lazy istek dolup
     *       tasarsa bile gecikme yasamaz.</li>
     *   <li>{@link #LAZY}: arka plan senkronu (squad, transfer, trophy,
     *       sidelined, lig refresh). Kalan kapasiteden yararlanir; burst'te
     *       sirada bekler.</li>
     * </ul>
     */
    public enum RequestPriority { LIVE, LAZY }

    public ApiFootballClient(ScorestvProperties properties,
                             ApiQuotaTracker quotaTracker) {
        this.quotaTracker = quotaTracker;
        ScorestvProperties.ApiFootball cfg = properties.apiFootball();
        this.keyConfigured = cfg.key() != null && !cfg.key().isBlank();

        this.liveTokensPerSecond = Math.max(1, cfg.liveTokensPerSecond());
        this.lazyTokensPerSecond = Math.max(1, cfg.lazyTokensPerSecond());
        this.liveBucket = new Semaphore(liveTokensPerSecond);
        this.lazyBucket = new Semaphore(lazyTokensPerSecond);

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

        this.bucketRefiller = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "apifb-bucket-refill");
            t.setDaemon(true);
            return t;
        });
        // Saniye-burst guard'i engellemek icin smooth refill: tek seferde N
        // token release etmek yerine, her 1000/N ms'de 1 token release ediyoruz.
        // Boylece API-Football saniyenin basinda 8 paralel istek yerine her
        // 125ms'de 1 istek goruyor — saniye-icin distribute edilmis akis.
        long liveIntervalMs = Math.max(1L, 1000L / liveTokensPerSecond);
        long lazyIntervalMs = Math.max(1L, 1000L / lazyTokensPerSecond);
        bucketRefiller.scheduleAtFixedRate(
                () -> refillSingle(liveBucket, liveTokensPerSecond),
                liveIntervalMs, liveIntervalMs, TimeUnit.MILLISECONDS);
        bucketRefiller.scheduleAtFixedRate(
                () -> refillSingle(lazyBucket, lazyTokensPerSecond),
                lazyIntervalMs, lazyIntervalMs, TimeUnit.MILLISECONDS);

        if (keyConfigured) {
            int totalPerSec = liveTokensPerSecond + lazyTokensPerSecond;
            log.info("ApiFootballClient hazir. baseUrl={} priority-aware token bucket: "
                            + "LIVE={}/sn ({} ms aralik) LAZY={}/sn ({} ms aralik) "
                            + "[toplam {}/sn ~ {}/dk]",
                    cfg.baseUrl(),
                    liveTokensPerSecond, liveIntervalMs,
                    lazyTokensPerSecond, lazyIntervalMs,
                    totalPerSec, totalPerSec * 60);
        } else {
            log.warn("API-Football anahtari tanimli degil (API_FOOTBALL_KEY bos). "
                    + "Futbol verisi cagrilari devre disi; .env dosyasina anahtari ekleyin.");
        }
    }

    /**
     * Refill bucket'lari uygulama kapanirken durdur. Spring shutdown sirasinda
     * cagrilir; daemon thread olsa bile temiz cikis icin gereklidir.
     */
    @PreDestroy
    void shutdown() {
        bucketRefiller.shutdownNow();
    }

    /**
     * API-Football'da bir endpoint'i GET ile cagirir ve ortak yanit zarfini doner.
     *
     * <p>Oncelik path'ten otomatik turetilir: {@code /fixtures*} → LIVE,
     * diger her yol → LAZY. Cagiran tarafi bilmek zorunda degildir.
     *
     * @param path        endpoint yolu, basinda "/" ile (orn. "/status", "/leagues")
     * @param queryParams sorgu parametreleri; bos olabilir ({@code Map.of()}).
     *                    Degeri {@code null} olan parametreler atlanir.
     * @param typeRef     beklenen yanit tipi
     * @param <T>         {@code response} alaninin tipi
     * @return basarili yanit zarfi (hata icermez)
     * @throws ApiFootballException yapilandirma, baglanti, kota veya saglayici hatasinda
     */
    public <T> ApiFootballResponse<T> get(String path,
                                          Map<String, ?> queryParams,
                                          ParameterizedTypeReference<ApiFootballResponse<T>> typeRef) {
        return get(path, queryParams, typeRef, detectPriority(path));
    }

    /**
     * Acikca priority belirten overload. Path-based detect'in dogru karar
     * vermedigi nadir durumlar icin (orn. lazy job'un /fixtures'tan veri
     * cekmesi gerektiginde) kullanilir.
     */
    public <T> ApiFootballResponse<T> get(String path,
                                          Map<String, ?> queryParams,
                                          ParameterizedTypeReference<ApiFootballResponse<T>> typeRef,
                                          RequestPriority priority) {
        if (!keyConfigured) {
            throw ApiFootballException.notConfigured(
                    "Futbol veri saglayicisi yapilandirilmamis.");
        }
        // 429 sonrasi global cooldown aktifse istegi HEMEN reddet — agi hic
        // dokunma. Boylece "429 aldiktan sonra hammer'lama → IP/key blok"
        // riski engellenir (api-football ratelimit dokumantasyonu).
        long cooldownMs = quotaTracker.cooldownRemainingMillis();
        if (cooldownMs > 0) {
            throw ApiFootballException.quotaExceeded(
                    "Futbol veri saglayicisi rate limit cooldown aktif ("
                            + cooldownMs + " ms kaldi).");
        }
        acquireToken(priority);

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
        } catch (RestClientResponseException ex) {
            // HTTP hata statusu (4xx/5xx). 429'u OZEL ele: cooldown baslat ve
            // quotaExceeded firlat ki SyncQueueWorker rate-limit backoff'una dussun.
            if (ex.getStatusCode().value() == 429) {
                Duration retryAfter = parseRetryAfter(ex.getResponseHeaders());
                _enterCooldown(retryAfter != null ? retryAfter : DEFAULT_429_COOLDOWN);
                log.warn("API-Football 429 (rate limit): path={} retryAfter={}",
                        path, retryAfter);
                throw ApiFootballException.quotaExceeded(
                        "Futbol veri saglayicisi rate limit (429 Too Many Requests).");
            }
            log.error("API-Football HTTP hata: path={} status={} hata={}",
                    path, ex.getStatusCode().value(), ex.getMessage());
            throw ApiFootballException.upstream(
                    "Futbol veri saglayicisi istegi reddetti.");
        } catch (RestClientException ex) {
            log.error("API-Football cagrisi basarisiz: path={} hata={}", path, ex.getMessage());
            throw ApiFootballException.upstream(
                    "Futbol veri saglayicisina ulasilamadi.");
        }

        logQuota(path, entity.getHeaders());

        ApiFootballResponse<T> body = entity.getBody();
        if (body == null) {
            throw ApiFootballException.upstream(
                    "Futbol veri saglayicisindan bos yanit alindi.");
        }
        if (body.hasErrors()) {
            throw classifyError(path, body.errorText());
        }
        return body;
    }

    /**
     * Oncelik kovasindan bir token alir. Kova bossa bir sonraki saniye refill'ini
     * bekler (en kotu durumda ~1 sn gecikme). LIVE ve LAZY kovalar bagimsiz
     * Semaphore'lar oldugu icin priority arasinda head-of-line blocking yoktur.
     */
    private void acquireToken(RequestPriority priority) {
        Semaphore bucket = (priority == RequestPriority.LIVE) ? liveBucket : lazyBucket;
        try {
            bucket.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ApiFootballException.upstream(
                    "Istek sira bekleme sirasinda kesildi.");
        }
    }

    /**
     * Token-basi smooth refill — bir kovaya bir token release eder, ama
     * sadece kapasite altinda ise. Dolu kovada no-op.
     *
     * <p>Saniye-basi tek refill yerine her 1000/N ms'de 1 token vermek API'nin
     * saniye-burst guard'ini engeller: 8 paralel istek yerine her 125ms'de
     * 1 istek goruyor. Dakika kotasi yine N×60 olarak korunur.
     */
    /**
     * Cooldown'a gir: global (Redis) cooldown'ı başlat, LOKAL cooldown penceresini
     * ayarla ve token bucket'ları BOŞALT. Boşaltma + cooldown boyunca refill'i
     * duraklatma birlikte, cooldown bitiminde ANİ boşalmayı engeller — kova 0'dan
     * nazikçe ramp eder, yeni 429 spirali kurulamaz.
     */
    private void _enterCooldown(Duration duration) {
        quotaTracker.startCooldown(duration);
        if (duration != null && !duration.isNegative() && !duration.isZero()) {
            localCooldownUntilMs = System.currentTimeMillis() + duration.toMillis();
        }
        liveBucket.drainPermits();
        lazyBucket.drainPermits();
    }

    private void refillSingle(Semaphore bucket, int maxCapacity) {
        // Cooldown penceresinde DOLDURMA — aksi halde cooldown bitince dolu kova
        // (LIVE+LAZY) bir anda boşalıp yeni 429'u tetikler (thundering herd → spiral).
        // Cooldown bitince kova 0'dan başlar, her tick 1 token ile nazikçe ramp eder.
        if (System.currentTimeMillis() < localCooldownUntilMs) {
            return;
        }
        if (bucket.availablePermits() < maxCapacity) {
            bucket.release(1);
        }
    }

    /**
     * Path'ten priority cikarir. Kural:
     * <ul>
     *   <li>TAM {@code /fixtures} (yani {@code ?live=all} ve {@code ?ids=}) → LIVE
     *       (canli-skor tablosu; LiveTicker + LiveDetailBatch)</li>
     *   <li>{@code /fixtures/*} alt-kaynaklari ve diger her yol → LAZY
     *       (on-demand mac/oyuncu detay hidrasyonu + background sync)</li>
     * </ul>
     */
    private RequestPriority detectPriority(String path) {
        if (path == null) return RequestPriority.LAZY;
        // Alt-kaynak uclari (/fixtures/events|statistics|players|lineups|
        // headtohead) MatchDetailLazySync tarafindan mac acilisinda PARALEL
        // firlatilir; LIVE olsalardi LIVE kovasini bir anda bosaltip burst
        // yaparlar. Bu yuzden YALNIZ tam "/fixtures" LIVE; alt-yollar LAZY.
        if (path.equals("/fixtures")) {
            return RequestPriority.LIVE;
        }
        return RequestPriority.LAZY;
    }

    /**
     * Yanit header'larindaki kalan kota bilgisini hem {@link ApiQuotaTracker}'a
     * push eder hem de loglar. Tracker degerleri merkezi karar mekanizmasinda
     * (SyncQueueWorker adaptif yavaslama, lazy sync atlamasi) kullanilir.
     */
    private void logQuota(String path, HttpHeaders headers) {
        quotaTracker.updateFromHeaders(headers);
        String dailyRemaining = headers.getFirst("x-ratelimit-requests-remaining");
        if (dailyRemaining == null) {
            return;
        }
        log.info("API-Football kota [{}]: gunluk kalan {}/{}, dakikalik kalan {}/{}",
                path,
                dailyRemaining,
                headers.getFirst("x-ratelimit-requests-limit"),
                headers.getFirst("X-RateLimit-Remaining"),
                headers.getFirst("X-RateLimit-Limit"));
    }

    /** {@code errors} alani dolu geldiginde uygun istisnayi uretir. */
    private ApiFootballException classifyError(String path, String detail) {
        log.warn("API-Football hata dondurdu: path={} errors={}", path, detail);
        String lower = detail.toLowerCase();
        if (lower.contains("limit") || lower.contains("quota") || lower.contains("rate")
                || lower.contains("maximum")) {
            // HTTP 200 + body errors.rateLimit durumu — cooldown'i burada da baslat.
            _enterCooldown(DEFAULT_429_COOLDOWN);
            return ApiFootballException.quotaExceeded(
                    "Futbol veri saglayicisinin istek kotasi doldu. Lutfen daha sonra tekrar deneyin.");
        }
        return ApiFootballException.upstream(
                "Futbol veri saglayicisi istegi reddetti.");
    }

    /**
     * {@code Retry-After} header'ini {@link Duration}'a cevirir. Hem saniye
     * (orn. {@code "120"}) hem HTTP-date formatini destekler. Yok/parse
     * edilemezse {@code null} doner.
     */
    private static Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst("Retry-After");
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException ignore) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration d = Duration.between(ZonedDateTime.now(), when);
                return d.isNegative() ? null : d;
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}

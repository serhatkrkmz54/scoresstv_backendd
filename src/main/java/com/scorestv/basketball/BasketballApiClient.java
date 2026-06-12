package com.scorestv.basketball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * API-Basketball v1 HTTP istemcisi (football'dan ayrı, kendi base-url'i).
 *
 * <p>API-Football ile AYNI API-Sports anahtarını kullanır ({@code x-apisports-key}).
 * Dakikalık limiti aşmamak için {@link #throttle()} ile istekler seri hale
 * getirilir.
 */
@Component
public class BasketballApiClient {

    private static final Logger log = LoggerFactory.getLogger(BasketballApiClient.class);
    private static final String API_KEY_HEADER = "x-apisports-key";

    private final RestClient http;
    private final boolean keyConfigured;
    private final long minIntervalMs;
    private volatile long lastRequestAt = 0L;

    public BasketballApiClient(BasketballProperties props) {
        this.keyConfigured = props.apiKey() != null && !props.apiKey().isBlank();
        this.minIntervalMs = props.requestsPerMinute() > 0
                ? Math.round(60_000.0 / props.requestsPerMinute())
                : 0;

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(10));
        rf.setReadTimeout(Duration.ofSeconds(15));

        var builder = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(rf);
        if (keyConfigured) {
            builder.defaultHeader(API_KEY_HEADER, props.apiKey());
        } else {
            log.warn("API-Basketball anahtarı tanımlı değil (apiKey boş) — istekler 'too many'/auth hatası alır.");
        }
        this.http = builder.build();
    }

    /**
     * Verilen path + query parametreleriyle GET; yanıtı {@link BasketballApiResponse}
     * olarak döner. Ağ/parse hatasında {@link org.springframework.web.client.RestClientException}
     * fırlatır — çağıran yakalar.
     */
    public <T> BasketballApiResponse<T> get(
            String path,
            Map<String, ?> queryParams,
            ParameterizedTypeReference<BasketballApiResponse<T>> typeRef) {
        throttle();
        ResponseEntity<BasketballApiResponse<T>> entity = http.get()
                .uri(builder -> {
                    builder.path(path);
                    if (queryParams != null) {
                        queryParams.forEach((k, v) -> {
                            if (v != null) builder.queryParam(k, v);
                        });
                    }
                    return builder.build();
                })
                .retrieve()
                .toEntity(typeRef);
        logQuota(entity.getHeaders());
        return entity.getBody();
    }

    /**
     * API-Sports kota header'larını loglar — basketbol GÜNLÜK limiti football'dan
     * BAĞIMSIZ (ayrı 75k). Düşükse WARN, normalde DEBUG.
     */
    private void logQuota(HttpHeaders headers) {
        String dayRemaining = headers.getFirst("x-ratelimit-requests-remaining");
        if (dayRemaining == null) return;
        String dayLimit = headers.getFirst("x-ratelimit-requests-limit");
        String minRemaining = headers.getFirst("X-RateLimit-Remaining");
        try {
            int rem = Integer.parseInt(dayRemaining);
            if (rem < 2000) {
                log.warn("Basketbol API kota DÜŞÜK: günlük kalan {}/{}, dakikalık kalan {}",
                        dayRemaining, dayLimit, minRemaining);
            } else {
                log.debug("Basketbol API kota: günlük kalan {}/{}, dakikalık kalan {}",
                        dayRemaining, dayLimit, minRemaining);
            }
        } catch (NumberFormatException ignore) {
            // header beklenmedik formatta — sessiz geç
        }
    }

    // ================================================================
    // B-Faz2: Detay endpoint helper'lari. SyncService'ler bu metodlarla
    // tip-guvenli sekilde cagri yapar. Hata yonetimi cagrida — bu metodlar
    // ham response (empty list/null safe) doner.
    // ================================================================

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGameDto>>
            GAMES_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<List<BkStandingDto>>>
            STANDINGS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGameTeamStatDto>>
            GAME_TEAM_STATS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGamePlayerStatDto>>
            GAME_PLAYER_STATS_TYPE = new ParameterizedTypeReference<>() {};

    /**
     * {@code /games?id=X} — tek mac detayli yanit (1 elemanli liste).
     * Slug cozumu, refresh, lazy-sync icin.
     */
    public List<BkGameDto> fetchGameById(long gameId) {
        var resp = get("/games", Map.of("id", gameId), GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /standings?league=X&season=Y} — puan durumu.
     * <b>Onemli:</b> response yapisi {@code List<List<BkStandingDto>>} —
     * her grup (NBA conference, EuroLeague Group A...) ayri alt-listedir.
     * Caller flatten ile islemeli.
     */
    public List<List<BkStandingDto>> fetchStandings(long leagueId, String season) {
        var resp = get("/standings",
                Map.of("league", leagueId, "season", season),
                STANDINGS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /games/statistics/teams?id=X} — mac basina takim istatistikleri
     * (2 satir: home + away).
     */
    public List<BkGameTeamStatDto> fetchGameTeamStats(long gameId) {
        var resp = get("/games/statistics/teams",
                Map.of("id", gameId), GAME_TEAM_STATS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /games/statistics/players?id=X} — mac basina oyuncu istatistikleri
     * (N satir: her takimdan starters + bench).
     */
    public List<BkGamePlayerStatDto> fetchGamePlayerStats(long gameId) {
        var resp = get("/games/statistics/players",
                Map.of("id", gameId), GAME_PLAYER_STATS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /games/h2h?h2h=A-B} — iki takim arasi gecmis maclar.
     * Yanit yapisi {@code /games} ile ayni; mevcut macin haricte tutulmasi
     * caller'in sorumlulugu (futbol H2H paterni).
     */
    public List<BkGameDto> fetchH2h(long team1Id, long team2Id) {
        var resp = get("/games/h2h",
                Map.of("h2h", team1Id + "-" + team2Id),
                GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** Dakikalık limiti korumak için istekleri eşit aralıklarla seri hale getirir. */
    private synchronized void throttle() {
        if (minIntervalMs <= 0) return;
        long now = System.currentTimeMillis();
        long wait = lastRequestAt + minIntervalMs - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}

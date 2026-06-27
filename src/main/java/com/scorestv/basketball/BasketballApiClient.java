package com.scorestv.basketball;

import com.scorestv.football.ApiQuotaTracker;
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
    /**
     * Paylasilan 429 cooldown — futbol/basketbol/voleybol AYNI API-Sports
     * key + AYNI sunucu IP'sinden cikiyor; API koruma katmani IP bazinda da
     * topluyor. Biri 429 yiyince hepsi ayni cooldown'a uymali, yoksa firewall
     * blok riski. ApiQuotaTracker cooldown'u Redis'te paylasimli tutar.
     */
    private final ApiQuotaTracker quotaTracker;

    public BasketballApiClient(BasketballProperties props,
                               ApiQuotaTracker quotaTracker) {
        this.quotaTracker = quotaTracker;
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
        guardCooldown();
        throttle();
        try {
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
        } catch (RestClientResponseException ex) {
            reportIf429(ex);
            throw ex;
        }
    }

    /**
     * Paylasilan 429 cooldown aktifse network'e HIC dokunmadan hizli reddet.
     * (Futbol bir 429 yeyip cooldown'a girince basketbol da beklemeli — ayni
     * IP+key. Aksi halde hammer'lama firewall blok riski.)
     */
    private void guardCooldown() {
        long ms = quotaTracker.cooldownRemainingMillis();
        if (ms > 0) {
            throw new RestClientException(
                    "API-Sports rate limit cooldown aktif (" + ms + " ms kaldi).");
        }
    }

    /** Yanit 429 ise paylasilan cooldown baslat (tum sporlar beklesin). */
    private void reportIf429(RestClientResponseException ex) {
        if (ex.getStatusCode().value() == 429) {
            quotaTracker.startCooldown(Duration.ofSeconds(10));
            log.warn("API-Basketball 429 (rate limit) — paylasilan cooldown baslatildi");
        }
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

    private static final ParameterizedTypeReference<BasketballApiResponse<String>>
            STAGE_GROUP_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGameTeamStatDto>>
            GAME_TEAM_STATS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkGamePlayerStatDto>>
            GAME_PLAYER_STATS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkLeagueDto>>
            LEAGUES_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkPlayerDto>>
            PLAYERS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkTeamDto>>
            TEAMS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkTeamStatisticsDto>>
            TEAM_STATS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<BasketballApiResponse<BkRosterPlayerDto>>
            ROSTER_TYPE = new ParameterizedTypeReference<>() {};

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
     *
     * <p><b>Defansif:</b> Bazi liglerde API {@code errors: ["No data..."]}
     * dondurur ve response shape farkli olabilir. Bu durumda parse hatasi
     * almak yerine bos liste donulur — caller bos cevap gibi davranir.
     * Doc: bazi ligler stage/group parametresi gerektirir; o uclar simdilik
     * destekli degil.
     */
    public List<List<BkStandingDto>> fetchStandings(long leagueId, String season) {
        return fetchStandings(leagueId, season, null, null);
    }

    /**
     * {@code /standings?league=X&season=Y&stage=Z&group=W} — opsiyonel stage
     * ve group filtreleri. NBA gibi liglerde "Regular Season" + "Playoffs"
     * ayri stage'ler, her birinin kendi standings'i var. Caller tum stage'leri
     * cekmek istiyorsa {@link #fetchStandingsStages} ile listeleri alip
     * her stage icin ayri cagri yapar.
     */
    public List<List<BkStandingDto>> fetchStandings(long leagueId, String season,
                                                     String stage, String group) {
        try {
            var params = new java.util.HashMap<String, Object>();
            params.put("league", leagueId);
            params.put("season", season);
            if (stage != null && !stage.isBlank()) params.put("stage", stage);
            if (group != null && !group.isBlank()) params.put("group", group);
            var resp = get("/standings", params, STANDINGS_TYPE);
            if (resp == null) return List.of();
            if (resp.hasErrors()) {
                log.debug("Standings errors league={} season={} stage={} group={}: {}",
                        leagueId, season, stage, group, resp.errors());
                return List.of();
            }
            return resp.responseOrEmpty();
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * {@code /standings/stages?league=X&season=Y} — bir lig + sezon icin
     * gecerli stage isimleri (orn. "NBA - Regular Season", "NBA - Playoffs").
     * Standings sync'i once bunlari cekip her stage icin ayri /standings cagrir.
     */
    public List<String> fetchStandingsStages(long leagueId, String season) {
        try {
            var resp = get("/standings/stages",
                    Map.of("league", leagueId, "season", season),
                    STAGE_GROUP_TYPE);
            if (resp == null || resp.hasErrors()) return List.of();
            return resp.responseOrEmpty();
        } catch (Exception e) {
            log.debug("Standings stages cagri hata league={} season={}: {}",
                    leagueId, season, e.toString());
            return List.of();
        }
    }

    /**
     * {@code /standings/groups?league=X&season=Y} — bir lig + sezon icin
     * gecerli group isimleri (orn. "Eastern Conference", "Western Conference",
     * "Group A"). Bazi liglerde stage yerine group ile bolunur.
     */
    public List<String> fetchStandingsGroups(long leagueId, String season) {
        try {
            var resp = get("/standings/groups",
                    Map.of("league", leagueId, "season", season),
                    STAGE_GROUP_TYPE);
            if (resp == null || resp.hasErrors()) return List.of();
            return resp.responseOrEmpty();
        } catch (Exception e) {
            log.debug("Standings groups cagri hata league={} season={}: {}",
                    leagueId, season, e.toString());
            return List.of();
        }
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

    /**
     * {@code /leagues?id=X} — tek lig full info (seasons array + ulke + coverage).
     * Lig detay sayfasi acilisinda seasons dropdown beslemesi + covered
     * tazeleme job'i icin.
     */
    public List<BkLeagueDto> fetchLeagueById(long leagueId) {
        var resp = get("/leagues", Map.of("id", leagueId), LEAGUES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /leagues?country=X} — bir ulkedeki tum ligler. Reference seed
     * ve country picker icin. Sezon listesi dahil.
     */
    public List<BkLeagueDto> fetchLeaguesByCountry(String countryName) {
        var resp = get("/leagues", Map.of("country", countryName), LEAGUES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /players?id=X&season=Y} — tek oyuncu profil + sezonluk istatistikler.
     * Player detay sayfasi + master tablo doldurma icin.
     */
    public List<BkPlayerDto> fetchPlayerById(long playerId, String season) {
        var resp = get("/players",
                Map.of("id", playerId, "season", season), PLAYERS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /players?league=X&season=Y&page=N} — bir lig + sezondaki tum
     * oyuncular (sayfali). Top players (scorers/rebounders/assists) sync'i
     * tum sayfalari toplar, in-memory siralar, top 10'u DB'ye yazar.
     *
     * <p>Sayfa basina ~20 oyuncu doner; NBA gibi 500+ oyunculu liglerde 25+
     * sayfa olabilir. Caller donguyle ilerler, bos sayfa veya null cevapta
     * durur.
     *
     * @param page 1-based sayfa numarasi
     */
    public List<BkPlayerDto> fetchPlayersByLeagueSeason(
            long leagueId, String season, int page) {
        var resp = get("/players",
                Map.of("league", leagueId, "season", season, "page", page),
                PLAYERS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /games?league=X&season=Y} — bir lig + sezondaki tum maclar.
     * Lig detay sayfasinin "Fikstur" tab'i icin.
     */
    public List<BkGameDto> fetchGamesByLeagueSeason(long leagueId, String season) {
        var resp = get("/games",
                Map.of("league", leagueId, "season", season),
                GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /teams?id=X} — tek takim full profil (ulke, founded, code, venue).
     * Takim detay sayfasi acilisi + DailyBasketballTeamRefreshJob icin.
     */
    public List<BkTeamDto> fetchTeamProfile(long teamId) {
        var resp = get("/teams", Map.of("id", teamId), TEAMS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /teams?league=X&season=Y&id=Z} — bir takimin belirli lig+sezon
     * baglaminda fetch'i. Cold-start senaryosunda kullanilir (junction
     * tablo bossa).
     */
    public List<BkTeamDto> fetchTeamInLeagueSeason(long teamId, long leagueId,
                                                     String season) {
        var resp = get("/teams",
                Map.of("id", teamId, "league", leagueId, "season", season),
                TEAMS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /teams/statistics?team=X&league=Y&season=Z} — bir takimin belirli
     * lig+sezondaki ozet istatistikleri (W/L, ortalamalar, ev/deplasman).
     *
     * <p>Yanit: tek nesne (liste degil). API resp wrapper'i list bekledigi icin
     * burada tek elemanli liste donulur — caller {@code getFirst()} ile alir.
     *
     * <p>Bazi yanitlarda "response: {}" sekilde gelir; bu durumda parse hatasi
     * almak yerine null nesneli liste donulur.
     */
    /**
     * {@code /statistics?team=X&league=Y&season=Z} — yanit {@code response}
     * alani TEK OBJEKT (liste degil). {@link BasketballApiResponse} List<T>
     * bekledigi icin burada Map olarak parse edip elle convert ediyoruz.
     */
    public java.util.Optional<BkTeamStatisticsDto> fetchTeamStatistics(
            long teamId, long leagueId, String season) {
        try {
            guardCooldown();
            throttle();
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = http.get()
                    .uri(b -> b.path("/statistics")
                            .queryParam("team", teamId)
                            .queryParam("league", leagueId)
                            .queryParam("season", season)
                            .build())
                    .retrieve()
                    .body(Map.class);
            if (raw == null) return java.util.Optional.empty();
            Object errors = raw.get("errors");
            if (errors instanceof java.util.List<?> el && !el.isEmpty()) {
                log.debug("Team statistics errors team={} league={} season={}: {}",
                        teamId, leagueId, season, el);
                return java.util.Optional.empty();
            }
            Object responseField = raw.get("response");
            if (!(responseField instanceof Map<?, ?>)) {
                return java.util.Optional.empty();
            }
            BkTeamStatisticsDto dto = STATS_MAPPER.convertValue(
                    responseField, BkTeamStatisticsDto.class);
            return java.util.Optional.ofNullable(dto);
        } catch (RestClientResponseException ex) {
            reportIf429(ex);
            return java.util.Optional.empty();
        } catch (Exception e) {
            log.debug("Team statistics cagri hata team={} league={} season={}: {}",
                    teamId, leagueId, season, e.toString());
            return java.util.Optional.empty();
        }
    }

    /** Local JSON mapper — Spring ObjectMapper bean'i ile cakismadan parse. */
    private static final com.fasterxml.jackson.databind.ObjectMapper STATS_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * {@code /players?team=X&season=Y} — bir takimin sezon kadrosu.
     *
     * <p>Yanit yapisi {@code /players?id=X}'den tamamen farkli — minimal
     * field'lar (id, name, number, country, position, age). Bu yuzden
     * {@link BkRosterPlayerDto} ayri tasarlandi.
     *
     * <p>Bazi liglerde 20-25 oyuncu doner; bos liste edilebilir hata yerine
     * cevap olarak gelir.
     */
    public java.util.List<BkRosterPlayerDto> fetchRosterByTeamSeason(
            long teamId, String season) {
        try {
            var resp = get("/players",
                    Map.of("team", teamId, "season", season),
                    ROSTER_TYPE);
            if (resp == null || resp.hasErrors()) return java.util.List.of();
            return resp.responseOrEmpty();
        } catch (Exception e) {
            log.debug("Roster fetch hata team={} season={}: {}",
                    teamId, season, e.toString());
            return java.util.List.of();
        }
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

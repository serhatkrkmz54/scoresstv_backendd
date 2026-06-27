package com.scorestv.volleyball;

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
 * API-Volleyball v1 HTTP istemcisi (football/basketball'dan ayri, kendi
 * base-url'i).
 *
 * <p>API-Football ile AYNI API-Sports anahtarini kullanir
 * ({@code x-apisports-key}). Dakikalik limiti asmamak icin {@link #throttle()}
 * ile istekler seri hale getirilir.
 *
 * <p>Voleybol API'si LEANER — oyuncu/kadro/mac-bazli-istatistik uclari YOK;
 * yalnizca games/standings/teams/leagues/countries/seasons +
 * /teams/statistics (sezon ozeti).
 */
@Component
public class VolleyballApiClient {

    private static final Logger log = LoggerFactory.getLogger(VolleyballApiClient.class);
    private static final String API_KEY_HEADER = "x-apisports-key";

    private final RestClient http;
    private final boolean keyConfigured;
    private final long minIntervalMs;
    private volatile long lastRequestAt = 0L;
    /**
     * Paylasilan 429 cooldown — futbol/basketbol/voleybol AYNI API-Sports
     * key + AYNI sunucu IP'sinden cikiyor (API koruma IP bazinda da topluyor).
     * Biri 429 yiyince hepsi ayni cooldown'a uymali (firewall blok riski).
     */
    private final ApiQuotaTracker quotaTracker;

    public VolleyballApiClient(VolleyballProperties props,
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
            log.warn("API-Volleyball anahtari tanimli degil (apiKey bos) — istekler auth hatasi alir.");
        }
        this.http = builder.build();
    }

    /**
     * Verilen path + query parametreleriyle GET; yaniti {@link VolleyballApiResponse}
     * olarak doner. Ag/parse hatasinda {@link org.springframework.web.client.RestClientException}
     * firlatir — cagiran yakalar.
     */
    public <T> VolleyballApiResponse<T> get(
            String path,
            Map<String, ?> queryParams,
            ParameterizedTypeReference<VolleyballApiResponse<T>> typeRef) {
        guardCooldown();
        throttle();
        try {
            ResponseEntity<VolleyballApiResponse<T>> entity = http.get()
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
     * (Futbol/basketbol bir 429 yeyip cooldown'a girince voleybol da beklemeli —
     * ayni IP+key. Aksi halde hammer'lama firewall blok riski.)
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
            log.warn("API-Volleyball 429 (rate limit) — paylasilan cooldown baslatildi");
        }
    }

    /** API-Sports kota header'larini loglar — voleybol gunluk limiti ayri. */
    private void logQuota(HttpHeaders headers) {
        String dayRemaining = headers.getFirst("x-ratelimit-requests-remaining");
        if (dayRemaining == null) return;
        String dayLimit = headers.getFirst("x-ratelimit-requests-limit");
        String minRemaining = headers.getFirst("X-RateLimit-Remaining");
        try {
            int rem = Integer.parseInt(dayRemaining);
            if (rem < 2000) {
                log.warn("Voleybol API kota DUSUK: gunluk kalan {}/{}, dakikalik kalan {}",
                        dayRemaining, dayLimit, minRemaining);
            } else {
                log.debug("Voleybol API kota: gunluk kalan {}/{}, dakikalik kalan {}",
                        dayRemaining, dayLimit, minRemaining);
            }
        } catch (NumberFormatException ignore) {
            // header beklenmedik formatta — sessiz gec
        }
    }

    // ================================================================
    // Tip-guvenli endpoint helper'lari.
    // ================================================================

    private static final ParameterizedTypeReference<VolleyballApiResponse<VbGameDto>>
            GAMES_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<List<VbStandingDto>>>
            STANDINGS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<String>>
            STAGE_GROUP_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<VbLeagueDto>>
            LEAGUES_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<VbTeamDto>>
            TEAMS_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<VbCountryDto>>
            COUNTRIES_TYPE = new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<VolleyballApiResponse<Integer>>
            SEASONS_TYPE = new ParameterizedTypeReference<>() {};

    /** {@code /games?id=X} — tek mac (1 elemanli liste). */
    public List<VbGameDto> fetchGameById(long gameId) {
        var resp = get("/games", Map.of("id", gameId), GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /games?date=YYYY-MM-DD&timezone=...} — bir gunun maclari. */
    public List<VbGameDto> fetchGamesByDate(String date, String timezone) {
        var resp = get("/games",
                Map.of("date", date, "timezone", timezone), GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /games?league=X&season=Y} — bir lig + sezondaki tum maclar. */
    public List<VbGameDto> fetchGamesByLeagueSeason(long leagueId, String season) {
        var resp = get("/games",
                Map.of("league", leagueId, "season", season), GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /games/h2h?h2h=A-B} — iki takim arasi gecmis maclar. */
    public List<VbGameDto> fetchH2h(long team1Id, long team2Id) {
        var resp = get("/games/h2h",
                Map.of("h2h", team1Id + "-" + team2Id), GAMES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /standings?league=X&season=Y} — puan durumu (List of List). */
    public List<List<VbStandingDto>> fetchStandings(long leagueId, String season) {
        return fetchStandings(leagueId, season, null, null);
    }

    /** {@code /standings?league=X&season=Y&stage=Z&group=W} — opsiyonel filtreler. */
    public List<List<VbStandingDto>> fetchStandings(long leagueId, String season,
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

    /** {@code /standings/stages?league=X&season=Y} — gecerli stage isimleri. */
    public List<String> fetchStandingsStages(long leagueId, String season) {
        try {
            var resp = get("/standings/stages",
                    Map.of("league", leagueId, "season", season), STAGE_GROUP_TYPE);
            if (resp == null || resp.hasErrors()) return List.of();
            return resp.responseOrEmpty();
        } catch (Exception e) {
            log.debug("Standings stages cagri hata league={} season={}: {}",
                    leagueId, season, e.toString());
            return List.of();
        }
    }

    /** {@code /standings/groups?league=X&season=Y} — gecerli group isimleri. */
    public List<String> fetchStandingsGroups(long leagueId, String season) {
        try {
            var resp = get("/standings/groups",
                    Map.of("league", leagueId, "season", season), STAGE_GROUP_TYPE);
            if (resp == null || resp.hasErrors()) return List.of();
            return resp.responseOrEmpty();
        } catch (Exception e) {
            log.debug("Standings groups cagri hata league={} season={}: {}",
                    leagueId, season, e.toString());
            return List.of();
        }
    }

    /** {@code /leagues?id=X} — tek lig full info (seasons array + ulke). */
    public List<VbLeagueDto> fetchLeagueById(long leagueId) {
        var resp = get("/leagues", Map.of("id", leagueId), LEAGUES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /leagues?country=X} — bir ulkedeki tum ligler. */
    public List<VbLeagueDto> fetchLeaguesByCountry(String countryName) {
        var resp = get("/leagues", Map.of("country", countryName), LEAGUES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /leagues} — tum ligler (referans seed). */
    public List<VbLeagueDto> fetchAllLeagues() {
        var resp = get("/leagues", Map.of(), LEAGUES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /teams?id=X} — tek takim profil. */
    public List<VbTeamDto> fetchTeamProfile(long teamId) {
        var resp = get("/teams", Map.of("id", teamId), TEAMS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /teams?league=X&season=Y} — bir lig + sezondaki takimlar. */
    public List<VbTeamDto> fetchTeamsByLeagueSeason(long leagueId, String season) {
        var resp = get("/teams",
                Map.of("league", leagueId, "season", season), TEAMS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /teams?id=Z&league=X&season=Y} — cold-start senaryosu. */
    public List<VbTeamDto> fetchTeamInLeagueSeason(long teamId, long leagueId,
                                                   String season) {
        var resp = get("/teams",
                Map.of("id", teamId, "league", leagueId, "season", season), TEAMS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /countries} — referans ulkeler. */
    public List<VbCountryDto> fetchCountries() {
        var resp = get("/countries", Map.of(), COUNTRIES_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /** {@code /seasons} — gecerli sezonlar (year listesi). */
    public List<Integer> fetchSeasons() {
        var resp = get("/seasons", Map.of(), SEASONS_TYPE);
        return resp == null ? List.of() : resp.responseOrEmpty();
    }

    /**
     * {@code /teams/statistics?team=X&league=Y&season=Z} — yanit {@code response}
     * alani TEK OBJEKT (liste degil). {@link VolleyballApiResponse} List<T>
     * bekledigi icin burada Map olarak parse edip elle convert ediyoruz.
     */
    public java.util.Optional<VbTeamStatisticsDto> fetchTeamStatistics(
            long teamId, long leagueId, String season) {
        try {
            guardCooldown();
            throttle();
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = http.get()
                    .uri(b -> b.path("/teams/statistics")
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
            VbTeamStatisticsDto dto = STATS_MAPPER.convertValue(
                    responseField, VbTeamStatisticsDto.class);
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

    /** Dakikalik limiti korumak icin istekleri esit araliklarla seri hale getirir. */
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

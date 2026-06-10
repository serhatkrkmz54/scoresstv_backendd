package com.scorestv.rankings.sync;

import com.scorestv.common.ApiException;
import com.scorestv.rankings.sync.dto.FifaRankingApiDto;
import com.scorestv.rankings.sync.dto.UefaCoefficientApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Disaridan ranking JSON'larini ceken HTTP istemcisi.
 *
 * <p>Hedef API'ler kimlik dogrulamasi gerektirmez ama user-agent ister
 * (bazi guvenlik filtreleri varsayilan Java HttpClient'i bloklar).
 *
 * <p>Hata yonetimi: 4xx/5xx → ApiException. Boyle bir durum gunluk job'in
 * sessizce bos yazmasini engeller.
 */
@Component
public class RankingsHttpClient {

    private static final Logger log = LoggerFactory.getLogger(RankingsHttpClient.class);

    private static final String FIFA_RANKINGS_URL =
            "https://api.fifa.com/api/v3/fifarankings/rankings/live"
                    + "?gender=1&sportType=0&language=en";

    private static final String UEFA_COEFFICIENT_URL =
            "https://comp.uefa.com/v2/coefficients";

    /**
     * Bot/firewall detection'dan kacinmak icin gercek bir tarayici UA stringi.
     * UEFA ve FIFA siteleri varsayilan Java HttpClient'i bloklayabilir.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";

    private final RestClient restClient;

    public RankingsHttpClient() {
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .build();
    }

    /** FIFA Erkek Milli Takim Siralamasi — tek istek. */
    public FifaRankingApiDto fetchFifaRanking() {
        log.debug("FIFA ranking fetch: {}", FIFA_RANKINGS_URL);
        try {
            return restClient.get()
                    .uri(FIFA_RANKINGS_URL)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw ApiException.upstream(
                                "FIFA ranking API hatasi: HTTP " + res.getStatusCode());
                    })
                    .body(FifaRankingApiDto.class);
        } catch (RuntimeException ex) {
            log.warn("FIFA ranking cagrisi basarisiz: {}", ex.getMessage());
            throw ex instanceof ApiException ? (ApiException) ex
                    : ApiException.upstream("FIFA ranking fetch hatasi: " + ex.getMessage());
        }
    }

    /**
     * UEFA katsayi — paginated. {@code coefficientType} MEN_CLUB veya
     * MEN_ASSOCIATION; her sayfada 50 kayit doner.
     */
    public UefaCoefficientApiDto fetchUefaCoefficient(
            String coefficientType, int page, int seasonYear) {
        String url = UEFA_COEFFICIENT_URL
                + "?coefficientRange=OVERALL"
                + "&coefficientType=" + coefficientType
                + "&language=EN"
                + "&page=" + page
                + "&pagesize=50"
                + "&seasonYear=" + seasonYear;
        log.debug("UEFA coefficient fetch: {}", url);
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw ApiException.upstream(
                                "UEFA coefficient API hatasi: HTTP " + res.getStatusCode());
                    })
                    .body(UefaCoefficientApiDto.class);
        } catch (RuntimeException ex) {
            log.warn("UEFA coefficient cagrisi basarisiz: {}", ex.getMessage());
            throw ex instanceof ApiException ? (ApiException) ex
                    : ApiException.upstream("UEFA coefficient fetch hatasi: " + ex.getMessage());
        }
    }
}

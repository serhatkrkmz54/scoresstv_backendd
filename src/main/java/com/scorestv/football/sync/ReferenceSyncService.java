package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.sync.dto.CountryApiDto;
import com.scorestv.football.sync.dto.LeagueApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Referans veriyi (ülkeler, ligler, sezonlar) API-Football'dan senkronlar.
 *
 * <p>Yalnızca iki API çağrısı yeter: {@code /countries} ve {@code /leagues}.
 * {@code /leagues} her ligin tüm sezonlarını gömülü döndürdüğü için ayrı bir
 * sezon çağrısına gerek yoktur. HTTP çağrısı transaction dışında; DB yazımı
 * {@link ReferenceUpserter} içinde (lig başına transaction).
 */
@Service
public class ReferenceSyncService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<CountryApiDto>>> COUNTRIES_TYPE =
            new ParameterizedTypeReference<ApiFootballResponse<List<CountryApiDto>>>() {
            };
    private static final ParameterizedTypeReference<ApiFootballResponse<List<LeagueApiDto>>> LEAGUES_TYPE =
            new ParameterizedTypeReference<ApiFootballResponse<List<LeagueApiDto>>>() {
            };

    private final ApiFootballClient client;
    private final ReferenceUpserter upserter;
    private final CountryRepository countryRepository;
    private final SeasonRepository seasonRepository;

    public ReferenceSyncService(ApiFootballClient client,
                                ReferenceUpserter upserter,
                                CountryRepository countryRepository,
                                SeasonRepository seasonRepository) {
        this.client = client;
        this.upserter = upserter;
        this.countryRepository = countryRepository;
        this.seasonRepository = seasonRepository;
    }

    /** {@code /countries} çağrısı + upsert. */
    public int syncCountries() {
        ApiFootballResponse<List<CountryApiDto>> response =
                client.get("/countries", Map.of(), COUNTRIES_TYPE);
        List<CountryApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Ülke senkronu: API boş liste döndü");
            return 0;
        }
        int upserted = upserter.upsertCountries(items);
        log.info("Ülke senkronu: {} ülke upsert edildi", upserted);
        return upserted;
    }

    /**
     * {@code /leagues} çağrısı + lig & sezon upsert. Her lig kendi
     * transaction'ında işlenir; bir lig başarısız olsa bile diğerlerine devam edilir.
     */
    public ReferenceSyncResult syncLeagues() {
        ApiFootballResponse<List<LeagueApiDto>> response =
                client.get("/leagues", Map.of(), LEAGUES_TYPE);
        List<LeagueApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Lig senkronu: API boş liste döndü");
            return new ReferenceSyncResult(0, 0, 0, 0);
        }

        int leaguesUpserted = 0;
        int leaguesFailed = 0;
        int seasonsUpserted = 0;
        for (LeagueApiDto dto : items) {
            try {
                seasonsUpserted += upserter.upsertLeague(dto);
                leaguesUpserted++;
            } catch (RuntimeException ex) {
                leaguesFailed++;
                Long id = (dto.league() != null) ? dto.league().id() : null;
                log.warn("Lig upsert edilemedi: leagueId={} hata={}", id, ex.getMessage());
            }
        }
        log.info("Lig senkronu: {} lig, {} sezon upsert edildi; {} lig başarısız",
                leaguesUpserted, seasonsUpserted, leaguesFailed);
        return new ReferenceSyncResult(0, leaguesUpserted, leaguesFailed, seasonsUpserted);
    }

    /**
     * Tek bir ligi (ve onun tüm sezonlarını + coverage'larını) API'den çeker.
     * {@code GET /leagues?id=X} yanıtı normalde tek elemanlı dizi; yine de
     * defansif olarak tümünü tarayıp upsert ediyoruz.
     *
     * @return yazılan sezon sayısı (lig bulunamadıysa 0)
     */
    public int syncOne(Long leagueId) {
        ApiFootballResponse<List<LeagueApiDto>> response = client.get(
                "/leagues", Map.of("id", leagueId), LEAGUES_TYPE);
        List<LeagueApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Tek-lig senkronu: API boş döndü leagueId={}", leagueId);
            return 0;
        }
        int seasons = 0;
        for (LeagueApiDto dto : items) {
            try {
                seasons += upserter.upsertLeague(dto);
            } catch (RuntimeException ex) {
                log.warn("Tek-lig upsert hatası: leagueId={} hata={}",
                        leagueId, ex.getMessage());
            }
        }
        log.info("Tek-lig senkronu: leagueId={} — {} sezon upsert", leagueId, seasons);
        return seasons;
    }

    /**
     * Bir takimin oynadigi TUM ligler + sezonlar — {@code GET /leagues?team=X}.
     *
     * <p>Takim detay sayfasinin "cold start" akisi icin: takim ilk defa
     * ziyaret edildiginde {@code /teams?id} sadece master bilgiyi verir, hangi
     * lig+sezonlarda oynadigini soylemez. Bu cagri eksigi tamamlar.
     *
     * <p>Yan etki olarak ligleri + sezonlari upsert eder. Doner: kesfedilen
     * lig id'leri + bu takim icin "current" olarak isaretlenmis sezonun yili
     * (varsa) + bu takimin oynadigi tum yillarin yeni → eski siralisi.
     */
    public TeamLeaguesResult syncByTeam(Long teamId) {
        ApiFootballResponse<List<LeagueApiDto>> response = client.get(
                "/leagues", Map.of("team", teamId), LEAGUES_TYPE);
        List<LeagueApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("/leagues?team={} bos dondu", teamId);
            return new TeamLeaguesResult(List.of(), null, List.of());
        }
        List<Long> leagueIds = new java.util.ArrayList<>();
        java.util.Set<Integer> allYears = new java.util.HashSet<>();
        Integer currentYear = null;
        for (LeagueApiDto dto : items) {
            try {
                upserter.upsertLeague(dto);
            } catch (RuntimeException ex) {
                log.warn("Lig upsert (team={}) hatasi: {}", teamId, ex.getMessage());
                continue;
            }
            if (dto.league() != null && dto.league().id() != null) {
                leagueIds.add(dto.league().id());
            }
            if (dto.seasons() != null) {
                for (LeagueApiDto.Season s : dto.seasons()) {
                    if (s == null || s.year() == null) continue;
                    allYears.add(s.year());
                    if (Boolean.TRUE.equals(s.current())) {
                        if (currentYear == null || s.year() > currentYear) {
                            currentYear = s.year();
                        }
                    }
                }
            }
        }
        List<Integer> sortedYears = allYears.stream()
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        log.info("/leagues?team={} sonuc: {} lig, current sezon={}, tum yillar={}",
                teamId, leagueIds.size(), currentYear, sortedYears);
        return new TeamLeaguesResult(leagueIds, currentYear, sortedYears);
    }

    /**
     * {@link #syncByTeam} sonucu — lazy sync orkestrasyonu icin kullanilir.
     *
     * @param leagueIds   takimin oynadigi liglerin id listesi
     * @param currentSeason takim icin gecerli sezonun yili (varsa)
     * @param allSeasons  takimin oynadigi tum yillar, yeni → eski
     */
    public record TeamLeaguesResult(
            List<Long> leagueIds,
            Integer currentSeason,
            List<Integer> allSeasons
    ) {}

    /** Hem ülkeleri hem ligleri+sezonları senkronlar (tam tarama). */
    public ReferenceSyncResult syncAll() {
        int countries = syncCountries();
        ReferenceSyncResult leagues = syncLeagues();
        return new ReferenceSyncResult(countries, leagues.leaguesUpserted(),
                leagues.leaguesFailed(), leagues.seasonsUpserted());
    }

    /**
     * Başlangıç senkronu: ülke tablosu boşsa ülkeler, sezon tablosu boşsa
     * ligler+sezonlar çekilir. Her ikisi de yalnızca bu senkrondan dolduğu için
     * "boş mu" sorusu temiz bir sinyaldir; yeniden başlatmalarda tekrar çekmez.
     */
    public ReferenceSyncResult syncIfEmpty() {
        int countries = 0;
        if (countryRepository.count() == 0) {
            countries = syncCountries();
        } else {
            log.info("Ülkeler zaten dolu; başlangıç ülke senkronu atlandı.");
        }

        ReferenceSyncResult leagues = new ReferenceSyncResult(0, 0, 0, 0);
        if (seasonRepository.count() == 0) {
            leagues = syncLeagues();
        } else {
            log.info("Sezonlar zaten dolu; başlangıç lig senkronu atlandı.");
        }

        return new ReferenceSyncResult(countries, leagues.leaguesUpserted(),
                leagues.leaguesFailed(), leagues.seasonsUpserted());
    }
}

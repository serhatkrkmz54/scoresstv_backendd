package com.scorestv.football.sync;

import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.sync.dto.CountryApiDto;
import com.scorestv.football.sync.dto.LeagueApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Referans veriyi (ülke / lig / sezon) veritabanına upsert eden transactional
 * bileşen. HTTP çağrısından ayrı tutulur.
 *
 * <p>Lig upsert'i <b>lig başına ayrı transaction</b> ile yapılır: {@code /leagues}
 * binlerce lig döndüğü için hepsini tek transaction'a sığdırmak ağır olurdu.
 */
@Service
public class ReferenceUpserter {

    private final CountryRepository countryRepository;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final ApplicationEventPublisher events;

    public ReferenceUpserter(CountryRepository countryRepository,
                             LeagueRepository leagueRepository,
                             SeasonRepository seasonRepository,
                             ApplicationEventPublisher events) {
        this.countryRepository = countryRepository;
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.events = events;
    }

    /**
     * Ülkeleri tek transaction'da upsert eder (liste küçüktür, ~170 kayıt).
     * Adı eksik kayıtlar atlanır.
     *
     * @return upsert edilen ülke sayısı
     */
    @Transactional
    public int upsertCountries(List<CountryApiDto> items) {
        int upserted = 0;
        for (CountryApiDto dto : items) {
            if (dto == null || dto.name() == null) {
                continue;
            }
            Country country = countryRepository.findByName(dto.name()).orElseGet(Country::new);
            country.setName(dto.name());
            country.setCode(dto.code());
            country.setFlagUrl(dto.flag());
            // flagKey ve nameTr'ye dokunulmaz — sırasıyla görsel aynalama ve
            // elle girilen Türkçe ad alanlarıdır; mevcut kayıtta korunur.
            countryRepository.save(country);
            events.publishEvent(new EntityIndexedEvent.CountryIndexed(country));
            upserted++;
        }
        return upserted;
    }

    /**
     * Tek bir ligi ve onun tüm sezonlarını kendi transaction'ında upsert eder.
     *
     * <p>{@code type} alanı yalnızca bu senkrondan gelir; {@code covered} bayrağı
     * ADMIN'e ait olduğu için DOKUNULMAZ.
     *
     * @return upsert edilen sezon sayısı (lig geçersizse 0)
     */
    @Transactional
    public int upsertLeague(LeagueApiDto dto) {
        LeagueApiDto.League incoming = dto.league();
        if (incoming == null || incoming.id() == null || incoming.name() == null) {
            return 0;
        }

        League league = leagueRepository.findById(incoming.id()).orElseGet(League::new);
        league.setId(incoming.id());
        league.setName(incoming.name());
        league.setType(incoming.type());
        league.setLogoUrl(incoming.logo());

        LeagueApiDto.Country country = dto.country();
        if (country != null) {
            league.setCountryName(country.name());
            league.setCountryCode(country.code());
            league.setCountryFlagUrl(country.flag());
        }
        league.setCurrentSeason(resolveCurrentSeason(dto.seasons()));
        // logoKey, covered ve nameTr'ye dokunulmaz — sırasıyla görsel aynalama,
        // ADMIN kapsam seçimi ve elle girilen Türkçe ad alanlarıdır.
        leagueRepository.save(league);
        events.publishEvent(new EntityIndexedEvent.LeagueIndexed(league));

        return upsertSeasons(league, dto.seasons());
    }

    /** Bir ligin sezonlarını upsert eder; mevcutlar tek sorguda önceden çekilir. */
    private int upsertSeasons(League league, List<LeagueApiDto.Season> seasons) {
        if (seasons == null || seasons.isEmpty()) {
            return 0;
        }
        Map<Integer, Season> existing = new HashMap<>();
        for (Season season : seasonRepository.findByLeagueIdOrderByYearDesc(league.getId())) {
            existing.put(season.getYear(), season);
        }

        int upserted = 0;
        for (LeagueApiDto.Season incoming : seasons) {
            if (incoming == null || incoming.year() == null) {
                continue;
            }
            Season season = existing.getOrDefault(incoming.year(), new Season());
            season.setLeague(league);
            season.setYear(incoming.year());
            season.setStartDate(parseDate(incoming.start()));
            season.setEndDate(parseDate(incoming.end()));
            season.setCurrent(Boolean.TRUE.equals(incoming.current()));
            applyCoverage(season, incoming.coverage());
            seasonRepository.save(season);
            upserted++;
        }
        return upserted;
    }

    /**
     * Sezon kapsam (coverage) bayraklarini set eder. Sezon baslamadiysa
     * coverage null gelir; o durumda hepsi false (default kalir).
     */
    private void applyCoverage(Season season, LeagueApiDto.Coverage c) {
        if (c == null) {
            season.setCoverageStandings(false);
            season.setCoverageEvents(false);
            season.setCoverageLineups(false);
            season.setCoverageStatsFixtures(false);
            season.setCoverageStatsPlayers(false);
            season.setCoveragePlayers(false);
            season.setCoverageTopScorers(false);
            season.setCoverageTopAssists(false);
            season.setCoverageTopCards(false);
            season.setCoverageInjuries(false);
            season.setCoveragePredictions(false);
            season.setCoverageOdds(false);
            return;
        }
        season.setCoverageStandings(Boolean.TRUE.equals(c.standings()));
        season.setCoveragePlayers(Boolean.TRUE.equals(c.players()));
        season.setCoverageTopScorers(Boolean.TRUE.equals(c.topScorers()));
        season.setCoverageTopAssists(Boolean.TRUE.equals(c.topAssists()));
        season.setCoverageTopCards(Boolean.TRUE.equals(c.topCards()));
        season.setCoverageInjuries(Boolean.TRUE.equals(c.injuries()));
        season.setCoveragePredictions(Boolean.TRUE.equals(c.predictions()));
        season.setCoverageOdds(Boolean.TRUE.equals(c.odds()));
        LeagueApiDto.Fixtures fx = c.fixtures();
        if (fx != null) {
            season.setCoverageEvents(Boolean.TRUE.equals(fx.events()));
            season.setCoverageLineups(Boolean.TRUE.equals(fx.lineups()));
            season.setCoverageStatsFixtures(Boolean.TRUE.equals(fx.statisticsFixtures()));
            season.setCoverageStatsPlayers(Boolean.TRUE.equals(fx.statisticsPlayers()));
        } else {
            season.setCoverageEvents(false);
            season.setCoverageLineups(false);
            season.setCoverageStatsFixtures(false);
            season.setCoverageStatsPlayers(false);
        }
    }

    /** Geçerli (current=true) sezonun yılı; yoksa en yeni sezon yılı. */
    private Integer resolveCurrentSeason(List<LeagueApiDto.Season> seasons) {
        if (seasons == null) {
            return null;
        }
        Integer maxYear = null;
        for (LeagueApiDto.Season season : seasons) {
            if (season == null || season.year() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(season.current())) {
                return season.year();
            }
            if (maxYear == null || season.year() > maxYear) {
                maxYear = season.year();
            }
        }
        return maxYear;
    }

    /** "yyyy-MM-dd" metnini LocalDate'e çevirir; null/geçersizse null döner. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}

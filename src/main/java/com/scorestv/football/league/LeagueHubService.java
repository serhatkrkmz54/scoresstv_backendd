package com.scorestv.football.league;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.web.dto.LeagueHubResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Standings sayfasi hub'i — TUM ligleri ulke bazinda gruplayip frontend
 * picker'a sunar. Standings verisi olan/olmayan ayrimi {@code hasStandings}
 * bayragiyla yapilir; verisi olmayan lige kullanici tikladiginda
 * LeagueDetailLazySync devreye girip standings'i otomatik ceker.
 *
 * <p>Cacheable LIVE (15sn) — kullanici ziyaretiyle covered=true degisikliklerinin
 * picker'da goruunmesini hizla yansitir.
 */
@Service
public class LeagueHubService {

    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final FootballMessages messages;
    private final MinioStorageService storage;

    public LeagueHubService(LeagueRepository leagueRepository,
                            CountryRepository countryRepository,
                            FootballMessages messages,
                            MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.messages = messages;
        this.storage = storage;
    }

    /**
     * Tum ligleri ulke bazinda gruplar. Standings verisi olan ligler
     * {@code hasStandings=true} bayragiyla isaretlenir.
     *
     * @param countryFilter null degilse sadece bu ulke kodlu ligler (orn. "TR")
     * @param search        null degilse lig adinda bu metinle eslesenler
     *                      (case-insensitive contains)
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'league-hub-' + (#countryFilter == null ? 'all' : #countryFilter) "
                + "+ '-' + (#search == null ? '' : #search) "
                + "+ '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public LeagueHubResponse hub(String countryFilter, String search, boolean turkish) {
        List<League> leagues = leagueRepository.findAllForHub();
        // Standings verisi olan lig id'leri — N+1 onlemek icin tek sorgu.
        Set<Long> withStandings = new HashSet<>(
                leagueRepository.findLeagueIdsWithStandings());

        // Country filter (kodla) — case insensitive
        if (countryFilter != null && !countryFilter.isBlank()) {
            String code = countryFilter.trim().toUpperCase(Locale.ROOT);
            leagues = leagues.stream()
                    .filter(l -> l.getCountryCode() != null
                            && code.equalsIgnoreCase(l.getCountryCode().trim()))
                    .toList();
        }
        // Search — lig adi icinde contains (case-insensitive)
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            leagues = leagues.stream()
                    .filter(l -> {
                        String n = (l.getName() != null
                                ? l.getName().toLowerCase(Locale.ROOT) : "");
                        String trName = (l.getNameTr() != null
                                ? l.getNameTr().toLowerCase(Locale.ROOT) : "");
                        return n.contains(q) || trName.contains(q);
                    })
                    .toList();
        }

        // Ulke adi → CountryGroup
        Map<String, CountryGroupAccumulator> byCountry = new LinkedHashMap<>();
        for (League l : leagues) {
            String countryKey = l.getCountryName() != null ? l.getCountryName() : "Unknown";
            byCountry.computeIfAbsent(countryKey, k -> new CountryGroupAccumulator())
                    .leagues.add(l);
        }

        List<LeagueHubResponse.CountryGroup> groups = new ArrayList<>(byCountry.size());
        for (Map.Entry<String, CountryGroupAccumulator> e : byCountry.entrySet()) {
            CountryGroupAccumulator acc = e.getValue();
            Country countryEntity = countryRepository.findByName(e.getKey()).orElse(null);
            String displayCountry = displayCountryName(countryEntity,
                    acc.leagues.get(0), turkish);
            String code = countryEntity != null ? countryEntity.getCode()
                    : acc.leagues.get(0).getCountryCode();
            String flag = countryFlagUrl(countryEntity);
            if (flag == null) {
                flag = acc.leagues.get(0).getCountryFlagUrl(); // eşleşmezse ligin ham bayrağı
            }
            List<LeagueHubResponse.LeagueRef> refs = new ArrayList<>(acc.leagues.size());
            for (League l : acc.leagues) {
                String name = displayName(l, turkish);
                refs.add(new LeagueHubResponse.LeagueRef(
                        l.getId(),
                        name,
                        SlugUtil.leagueSlug(name, l.getId()),
                        l.getLogoKey() != null ? storage.publicUrl(l.getLogoKey()) : null,
                        messages.leagueType(l.getType(), turkish),
                        l.getType(),
                        l.getCurrentSeason(),
                        l.isCovered(),
                        withStandings.contains(l.getId())));
            }
            groups.add(new LeagueHubResponse.CountryGroup(
                    displayCountry, code, flag, refs));
        }
        return new LeagueHubResponse(leagues.size(), groups);
    }

    private String countryFlagUrl(Country country) {
        if (country == null) {
            return null;
        }
        if (country.getFlagKey() != null) {
            return storage.publicUrl(country.getFlagKey());
        }
        if (country.getFlagUrl() != null && !country.getFlagUrl().isBlank()) {
            return country.getFlagUrl();
        }
        String code = country.getCode();
        if (code != null && code.length() == 2) {
            return "https://flagcdn.com/w160/"
                    + code.toLowerCase(java.util.Locale.ROOT) + ".png";
        }
        return null;
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) return tr;
        }
        return entity.getName();
    }

    private static String displayCountryName(Country country, League league, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (country != null && country.getName() != null) {
            return country.getName();
        }
        return league.getCountryName();
    }

    /** Accumulator — ulke bazinda lig listesi toplama. */
    private static class CountryGroupAccumulator {
        final List<League> leagues = new ArrayList<>();
    }
}

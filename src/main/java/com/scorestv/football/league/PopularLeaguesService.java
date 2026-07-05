package com.scorestv.football.league;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.web.dto.PopularLeagueView;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-league-ids) popüler ligleri,
 * verilen sırayı koruyarak lokalize döner. Sol ray için.
 */
@Service
public class PopularLeaguesService {

    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final FootballProperties properties;
    private final MinioStorageService storage;

    public PopularLeaguesService(LeagueRepository leagueRepository,
                                 CountryRepository countryRepository,
                                 FootballProperties properties,
                                 MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<PopularLeagueView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularLeagueIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, League> byId = new HashMap<>();
        for (League league : leagueRepository.findAllById(ids)) {
            byId.put(league.getId(), league);
        }

        Map<String, Country> countriesByName = new HashMap<>();
        for (Country country : countryRepository.findAll()) {
            countriesByName.put(country.getName(), country);
        }

        List<PopularLeagueView> out = new ArrayList<>();
        for (Long id : ids) { // config sırasını koru
            League league = byId.get(id);
            if (league == null) {
                continue; // bilinmeyen/silinmiş id'yi atla
            }
            String name = displayName(league, turkish);
            Country country = (league.getCountryName() != null)
                    ? countriesByName.get(league.getCountryName())
                    : null;
            out.add(new PopularLeagueView(
                    league.getId(),
                    name,
                    SlugUtil.leagueSlug(name, league.getId()),
                    league.getLogoKey() != null ? storage.publicUrl(league.getLogoKey()) : null,
                    countryName(league, country, turkish),
                    countryFlagUrl(country)));
        }
        return out;
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }

    private static String countryName(League league, Country country, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        return league.getCountryName();
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
}

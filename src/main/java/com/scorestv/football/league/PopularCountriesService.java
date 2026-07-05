package com.scorestv.football.league;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.web.dto.PopularCountryView;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Config'te elle belirlenen (popular-country-ids) ülkeleri, verilen sırayı
 * koruyarak lokalize döner. Sol ray için.
 */
@Service
public class PopularCountriesService {

    private final CountryRepository countryRepository;
    private final FootballProperties properties;
    private final MinioStorageService storage;

    public PopularCountriesService(CountryRepository countryRepository,
                                   FootballProperties properties,
                                   MinioStorageService storage) {
        this.countryRepository = countryRepository;
        this.properties = properties;
        this.storage = storage;
    }

    public List<PopularCountryView> getPopular(boolean turkish) {
        List<Long> ids = properties.serving().popularCountryIds();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Country> byId = new HashMap<>();
        for (Country country : countryRepository.findAllById(ids)) {
            byId.put(country.getId(), country);
        }

        List<PopularCountryView> out = new ArrayList<>();
        for (Long id : ids) { // config sırasını koru
            Country country = byId.get(id);
            if (country == null) {
                continue;
            }
            String name = displayName(country, turkish);
            String slugBase = SlugUtil.slugify(name);
            String slug = (slugBase.isEmpty() ? "country" : slugBase) + "-" + id;
            out.add(new PopularCountryView(
                    country.getId(),
                    name,
                    slug,
                    countryFlagUrl(country),
                    country.getCode()));
        }
        return out;
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

    private static String displayName(Country country, boolean turkish) {
        if (turkish) {
            String tr = country.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return country.getName();
    }
}

package com.scorestv.mobile.web;

import com.scorestv.football.league.LeagueHubService;
import com.scorestv.football.web.dto.LeagueHubResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile-ozel lig endpoint'leri.
 *
 * <p>Web/admin tarafindaki {@link com.scorestv.football.league.StandingsHubController}
 * tum ligleri (League + Cup) doner. Mobile'da kullanici favori takim sectigi
 * icin <b>sadece duzenli ligler</b> (League tipi) listelenir; kupalarda
 * takim kadrosu sabit degil (eleme usulu) ve kullanici icin anlamli degil.
 *
 * <p>URL: {@code GET /api/v1/mobile/leagues/hub?lang=&country=&search=}
 *
 * <p>Bu controller mevcut servisleri delegate eder; bagimsiz logic yok.
 * Boylece web tarafini etkilemeden mobile filtre eklenebilir.
 */
@RestController
@RequestMapping("/api/v1/mobile/leagues")
public class MobileLeagueController {

    private final LeagueHubService hubService;

    public MobileLeagueController(LeagueHubService hubService) {
        this.hubService = hubService;
    }

    /**
     * Lig hub'i — sadece duzenli ligler (Cup hariclenir).
     */
    @GetMapping("/hub")
    public LeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        final LeagueHubResponse base = hubService.hub(
                country, search, "tr".equalsIgnoreCase(lang));
        return excludeCups(base);
    }

    /**
     * Verilen yanittan Cup tipi ligleri ayikla. Bos kalan ulkeler de duser.
     *
     * <p>{@code LeagueRef.rawType} backend'den "League" / "Cup" geliyor;
     * mobile sadece "League" olanlari gosterir.
     */
    private static LeagueHubResponse excludeCups(LeagueHubResponse base) {
        if (base == null || base.countries() == null) return base;
        final List<LeagueHubResponse.CountryGroup> filteredCountries =
                new ArrayList<>(base.countries().size());
        int total = 0;
        for (LeagueHubResponse.CountryGroup country : base.countries()) {
            final List<LeagueHubResponse.LeagueRef> leagues = country.leagues().stream()
                    .filter(l -> !"cup".equalsIgnoreCase(l.rawType()))
                    .toList();
            if (leagues.isEmpty()) continue;
            filteredCountries.add(new LeagueHubResponse.CountryGroup(
                    country.name(), country.code(), country.flag(), leagues));
            total += leagues.size();
        }
        return new LeagueHubResponse(total, filteredCountries);
    }
}

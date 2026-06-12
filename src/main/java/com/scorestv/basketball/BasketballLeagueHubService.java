package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballCountry;
import com.scorestv.basketball.domain.BasketballCountryRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.web.dto.BasketballLeagueHubResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Basketbol lig hub'i — futbol {@link com.scorestv.football.league.LeagueHubService}'in
 * basketbol karşılığı. Tüm basketbol liglerini ülke bazında gruplayıp mobile
 * onboarding accordion'una sunar.
 *
 * <p>Yapı futboldan basitleştirilmiş:
 * <ul>
 *   <li>"covered" / "hasStandings" bayrakları yok — basketbol mobile şu an
 *       sadece canlı skor + favori maç bildirimi içeriyor</li>
 *   <li>Slug yok — endpoint numeric leagueId kullanır</li>
 *   <li>League type yerel string olarak döner (örn "League", "Cup")</li>
 * </ul>
 *
 * <p>Cache stratejisi: şimdilik cache yok — basketbol kullanıcı trafiği düşük,
 * gereksiz katman eklemek istemiyorum. İhtiyaç olursa eklenebilir.
 */
@Service
public class BasketballLeagueHubService {

    private final BasketballLeagueRepository leagueRepository;
    private final BasketballCountryRepository countryRepository;
    private final MinioStorageService storage;

    public BasketballLeagueHubService(BasketballLeagueRepository leagueRepository,
                                      BasketballCountryRepository countryRepository,
                                      MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.storage = storage;
    }

    /**
     * Tüm basketbol liglerini ülke bazında gruplar.
     *
     * @param countryFilter null değilse sadece bu ülke kodu (örn "TR")
     * @param search        null değilse lig adında bu metinle eşleşenler
     *                      (case-insensitive contains)
     * @param turkish       TR locale ise true → name_tr/country_name_tr tercih edilir
     */
    @Transactional(readOnly = true)
    public BasketballLeagueHubResponse hub(String countryFilter, String search, boolean turkish) {
        List<BasketballLeague> leagues = leagueRepository.findAll();

        // Country filter (kod) — case insensitive
        if (countryFilter != null && !countryFilter.isBlank()) {
            String code = countryFilter.trim().toUpperCase(Locale.ROOT);
            leagues = leagues.stream()
                    .filter(l -> l.getCountryCode() != null
                            && code.equalsIgnoreCase(l.getCountryCode().trim()))
                    .toList();
        }
        // Search — lig adı (en/tr) içinde contains
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            leagues = leagues.stream()
                    .filter(l -> {
                        String n = l.getName() != null
                                ? l.getName().toLowerCase(Locale.ROOT) : "";
                        String tr = l.getNameTr() != null
                                ? l.getNameTr().toLowerCase(Locale.ROOT) : "";
                        return n.contains(q) || tr.contains(q);
                    })
                    .toList();
        }

        // Ülke adı → CountryGroup
        Map<String, List<BasketballLeague>> byCountry = new LinkedHashMap<>();
        for (BasketballLeague l : leagues) {
            String key = l.getCountryName() != null ? l.getCountryName() : "Unknown";
            byCountry.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }

        // Ülke entity'lerini name'e göre tek seferde indeksle (N+1 önle).
        Map<String, BasketballCountry> countryByName = new HashMap<>();
        for (BasketballCountry c : countryRepository.findAll()) {
            if (c.getName() != null) countryByName.put(c.getName(), c);
        }

        List<BasketballLeagueHubResponse.CountryGroup> groups = new ArrayList<>(byCountry.size());
        for (Map.Entry<String, List<BasketballLeague>> e : byCountry.entrySet()) {
            List<BasketballLeague> bucket = e.getValue();
            BasketballCountry countryEntity = countryByName.get(e.getKey());
            String displayCountry = displayCountryName(countryEntity, bucket.get(0), turkish);
            String code = countryEntity != null ? countryEntity.getCode()
                    : bucket.get(0).getCountryCode();
            String flag = bucket.get(0).getCountryFlagKey() != null
                    ? storage.publicUrl(bucket.get(0).getCountryFlagKey())
                    : (countryEntity != null && countryEntity.getFlag() != null
                            ? countryEntity.getFlag()
                            : bucket.get(0).getCountryFlag());

            List<BasketballLeagueHubResponse.LeagueRef> refs = new ArrayList<>(bucket.size());
            for (BasketballLeague l : bucket) {
                String name = displayName(l, turkish);
                String logo = l.getLogoKey() != null ? storage.publicUrl(l.getLogoKey())
                        : l.getLogo();
                refs.add(new BasketballLeagueHubResponse.LeagueRef(
                        l.getId(),
                        name,
                        logo,
                        l.getType(),
                        l.getCurrentSeason()));
            }
            groups.add(new BasketballLeagueHubResponse.CountryGroup(
                    displayCountry, code, flag, refs));
        }
        return new BasketballLeagueHubResponse(leagues.size(), groups);
    }

    private static String displayName(BasketballLeague l, boolean turkish) {
        if (turkish && l.getNameTr() != null && !l.getNameTr().isBlank()) {
            return l.getNameTr();
        }
        return l.getName();
    }

    private static String displayCountryName(BasketballCountry country,
                                             BasketballLeague league,
                                             boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (turkish && league.getCountryNameTr() != null
                && !league.getCountryNameTr().isBlank()) {
            return league.getCountryNameTr();
        }
        if (country != null && country.getName() != null) return country.getName();
        return league.getCountryName();
    }
}

package com.scorestv.volleyball;

import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.domain.VolleyballCountry;
import com.scorestv.volleyball.domain.VolleyballCountryRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.web.dto.VolleyballLeagueHubResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Voleybol lig hub'i — basketbol {@code BasketballLeagueHubService}'in voleybol
 * esi. Tum voleybol liglerini ulke bazinda gruplayip mobile onboarding
 * accordion'una sunar.
 */
@Service
public class VolleyballLeagueHubService {

    private final VolleyballLeagueRepository leagueRepository;
    private final VolleyballCountryRepository countryRepository;
    private final MinioStorageService storage;
    private final VolleyballMessages messages;

    public VolleyballLeagueHubService(VolleyballLeagueRepository leagueRepository,
                                      VolleyballCountryRepository countryRepository,
                                      MinioStorageService storage,
                                      VolleyballMessages messages) {
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.storage = storage;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public VolleyballLeagueHubResponse hub(String countryFilter, String search, boolean turkish) {
        List<VolleyballLeague> leagues = leagueRepository.findAll();

        if (countryFilter != null && !countryFilter.isBlank()) {
            String code = countryFilter.trim().toUpperCase(Locale.ROOT);
            leagues = leagues.stream()
                    .filter(l -> l.getCountryCode() != null
                            && code.equalsIgnoreCase(l.getCountryCode().trim()))
                    .toList();
        }
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

        Map<String, List<VolleyballLeague>> byCountry = new LinkedHashMap<>();
        for (VolleyballLeague l : leagues) {
            String key = l.getCountryName() != null ? l.getCountryName() : "Unknown";
            byCountry.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }

        Map<String, VolleyballCountry> countryByName = new HashMap<>();
        for (VolleyballCountry c : countryRepository.findAll()) {
            if (c.getName() != null) countryByName.put(c.getName(), c);
        }

        List<VolleyballLeagueHubResponse.CountryGroup> groups = new ArrayList<>(byCountry.size());
        for (Map.Entry<String, List<VolleyballLeague>> e : byCountry.entrySet()) {
            List<VolleyballLeague> bucket = e.getValue();
            VolleyballCountry countryEntity = countryByName.get(e.getKey());
            String displayCountry = displayCountryName(countryEntity, bucket.get(0), turkish);
            String code = countryEntity != null ? countryEntity.getCode()
                    : bucket.get(0).getCountryCode();
            String flag = bucket.get(0).getCountryFlagKey() != null
                    ? storage.publicUrl(bucket.get(0).getCountryFlagKey())
                    : (countryEntity != null && countryEntity.getFlag() != null
                            ? countryEntity.getFlag()
                            : bucket.get(0).getCountryFlag());

            List<VolleyballLeagueHubResponse.LeagueRef> refs = new ArrayList<>(bucket.size());
            for (VolleyballLeague l : bucket) {
                String name = displayName(l, turkish);
                String logo = l.getLogoKey() != null ? storage.publicUrl(l.getLogoKey())
                        : l.getLogo();
                refs.add(new VolleyballLeagueHubResponse.LeagueRef(
                        l.getId(), name, logo, messages.leagueType(l.getType(), turkish),
                        l.getCurrentSeason()));
            }
            groups.add(new VolleyballLeagueHubResponse.CountryGroup(
                    displayCountry, code, flag, refs));
        }
        return new VolleyballLeagueHubResponse(leagues.size(), groups);
    }

    private static String displayName(VolleyballLeague l, boolean turkish) {
        if (turkish && l.getNameTr() != null && !l.getNameTr().isBlank()) {
            return l.getNameTr();
        }
        return l.getName();
    }

    private static String displayCountryName(VolleyballCountry country,
                                             VolleyballLeague league,
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

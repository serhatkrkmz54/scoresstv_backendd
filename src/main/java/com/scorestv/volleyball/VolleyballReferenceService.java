package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballCountry;
import com.scorestv.volleyball.domain.VolleyballCountryRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Voleybol referans (master) verisi seed'i: ulkeler ({@code /countries}) ve
 * ligler ({@code /leagues}). Haftada bir / acilista cekilir. Ligin guncel
 * sezonu da burada saptanir (teams/standings icin).
 */
@Service
public class VolleyballReferenceService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballReferenceService.class);

    private final VolleyballApiClient client;
    private final VolleyballCountryRepository countryRepo;
    private final VolleyballLeagueRepository leagueRepo;

    public VolleyballReferenceService(VolleyballApiClient client,
                                      VolleyballCountryRepository countryRepo,
                                      VolleyballLeagueRepository leagueRepo) {
        this.client = client;
        this.countryRepo = countryRepo;
        this.leagueRepo = leagueRepo;
    }

    @Transactional
    public int syncCountries() {
        List<VbCountryDto> list;
        try {
            list = client.fetchCountries();
        } catch (Exception e) {
            log.warn("Voleybol /countries basarisiz: {}", e.toString());
            return 0;
        }
        int n = 0;
        Set<Long> seen = new HashSet<>();
        for (VbCountryDto c : list) {
            if (c.id() == null || c.name() == null || !seen.add(c.id())) continue;
            VolleyballCountry e = countryRepo.findById(c.id()).orElseGet(VolleyballCountry::new);
            e.setId(c.id());
            e.setName(c.name());
            e.setCode(c.code());
            e.setFlag(c.flag());
            countryRepo.save(e);
            n++;
        }
        log.info("Voleybol ulke seed: {} ulke", n);
        return n;
    }

    @Transactional
    public int syncLeagues() {
        List<VbLeagueDto> list;
        try {
            list = client.fetchAllLeagues();
        } catch (Exception e) {
            log.warn("Voleybol /leagues basarisiz: {}", e.toString());
            return 0;
        }
        int n = 0;
        Set<Long> seen = new HashSet<>();
        for (VbLeagueDto l : list) {
            if (l.id() == null || !seen.add(l.id())) continue;
            VolleyballLeague e = leagueRepo.findById(l.id()).orElseGet(VolleyballLeague::new);
            e.setId(l.id());
            e.setName(l.name() != null ? l.name() : ("Lig #" + l.id()));
            e.setType(l.type());
            e.setLogo(l.logo());
            if (l.country() != null) {
                e.setCountryName(l.country().name());
                e.setCountryCode(l.country().code());
                e.setCountryFlag(l.country().flag());
            }
            e.setCurrentSeason(resolveCurrentSeason(l.seasons()));
            leagueRepo.save(e);
            n++;
        }
        log.info("Voleybol lig seed: {} lig", n);
        return n;
    }

    /** {@code current=true} olan sezon; yoksa listedeki son (en yeni) sezon. */
    private String resolveCurrentSeason(List<VbLeagueDto.Season> seasons) {
        if (seasons == null) return null;
        String last = null;
        for (VbLeagueDto.Season s : seasons) {
            if (s.seasonAsString() == null) continue;
            if (Boolean.TRUE.equals(s.current())) return s.seasonAsString();
            last = s.seasonAsString();
        }
        return last;
    }
}

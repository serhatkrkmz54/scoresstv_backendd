package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballCountry;
import com.scorestv.basketball.domain.BasketballCountryRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Basketbol referans (master) verisi seed'i: ülkeler ({@code /countries}) ve
 * ligler ({@code /leagues}). Haftada bir / açılışta çekilir — games sync'inden
 * bağımsız. Ligin güncel sezonu da burada saptanır (teams/standings için).
 */
@Service
public class BasketballReferenceService {

    private static final Logger log = LoggerFactory.getLogger(BasketballReferenceService.class);

    private static final ParameterizedTypeReference<BasketballApiResponse<BkCountryDto>> COUNTRIES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<BasketballApiResponse<BkLeagueDto>> LEAGUES_TYPE =
            new ParameterizedTypeReference<>() {};

    private final BasketballApiClient client;
    private final BasketballCountryRepository countryRepo;
    private final BasketballLeagueRepository leagueRepo;

    public BasketballReferenceService(BasketballApiClient client,
                                      BasketballCountryRepository countryRepo,
                                      BasketballLeagueRepository leagueRepo) {
        this.client = client;
        this.countryRepo = countryRepo;
        this.leagueRepo = leagueRepo;
    }

    @Transactional
    public int syncCountries() {
        List<BkCountryDto> list;
        try {
            list = client.get("/countries", Map.of(), COUNTRIES_TYPE).responseOrEmpty();
        } catch (Exception e) {
            log.warn("Basketbol /countries başarısız: {}", e.toString());
            return 0;
        }
        int n = 0;
        Set<Long> seen = new HashSet<>();
        for (BkCountryDto c : list) {
            if (c.id() == null || c.name() == null || !seen.add(c.id())) continue;
            BasketballCountry e = countryRepo.findById(c.id()).orElseGet(BasketballCountry::new);
            e.setId(c.id());
            e.setName(c.name());
            e.setCode(c.code());
            e.setFlag(c.flag());
            countryRepo.save(e);
            n++;
        }
        log.info("Basketbol ülke seed: {} ülke", n);
        return n;
    }

    @Transactional
    public int syncLeagues() {
        List<BkLeagueDto> list;
        try {
            list = client.get("/leagues", Map.of(), LEAGUES_TYPE).responseOrEmpty();
        } catch (Exception e) {
            log.warn("Basketbol /leagues başarısız: {}", e.toString());
            return 0;
        }
        int n = 0;
        Set<Long> seen = new HashSet<>();
        for (BkLeagueDto l : list) {
            if (l.id() == null || !seen.add(l.id())) continue;
            BasketballLeague e = leagueRepo.findById(l.id()).orElseGet(BasketballLeague::new);
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
        log.info("Basketbol lig seed: {} lig", n);
        return n;
    }

    /** {@code current=true} olan sezon; yoksa listedeki son (en yeni) sezon. */
    private String resolveCurrentSeason(List<BkLeagueDto.Season> seasons) {
        if (seasons == null) return null;
        String last = null;
        for (BkLeagueDto.Season s : seasons) {
            if (s.season() == null) continue;
            if (Boolean.TRUE.equals(s.current())) return s.season();
            last = s.season();
        }
        return last;
    }
}

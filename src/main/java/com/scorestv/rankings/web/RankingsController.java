package com.scorestv.rankings.web;

import com.scorestv.rankings.web.dto.FifaRankingResponse;
import com.scorestv.rankings.web.dto.UefaClubRankingResponse;
import com.scorestv.rankings.web.dto.UefaCountryRankingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public rankings endpoint'leri.
 *
 * <ul>
 *   <li>{@code GET /api/v1/rankings/fifa?confederation=&search=&lang=}</li>
 *   <li>{@code GET /api/v1/rankings/uefa/clubs?season=&country=&search=&lang=}</li>
 *   <li>{@code GET /api/v1/rankings/uefa/countries?season=&search=&lang=}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingsController {

    private final RankingsService service;

    public RankingsController(RankingsService service) {
        this.service = service;
    }

    @GetMapping("/fifa")
    public FifaRankingResponse fifa(
            @RequestParam(required = false) String confederation,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return service.fifaRanking(confederation, search, "tr".equalsIgnoreCase(lang));
    }

    @GetMapping("/uefa/clubs")
    public UefaClubRankingResponse uefaClubs(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return service.uefaClubRanking(season, country, search, "tr".equalsIgnoreCase(lang));
    }

    @GetMapping("/uefa/countries")
    public UefaCountryRankingResponse uefaCountries(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return service.uefaCountryRanking(season, search, "tr".equalsIgnoreCase(lang));
    }
}

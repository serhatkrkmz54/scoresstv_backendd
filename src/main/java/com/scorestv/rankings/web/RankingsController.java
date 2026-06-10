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
 *   <li>{@code GET /api/v1/rankings/fifa?confederation=&search=} — FIFA milli</li>
 *   <li>{@code GET /api/v1/rankings/uefa/clubs?season=&country=&search=} — UEFA kulup</li>
 *   <li>{@code GET /api/v1/rankings/uefa/countries?season=&search=} — UEFA milli</li>
 * </ul>
 *
 * <p>Tum endpointler public — security config'de permitAll.
 */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingsController {

    private final RankingsService service;

    public RankingsController(RankingsService service) {
        this.service = service;
    }

    /** FIFA Erkek Milli Takim Siralamasi. */
    @GetMapping("/fifa")
    public FifaRankingResponse fifa(
            @RequestParam(required = false) String confederation,
            @RequestParam(required = false) String search) {
        return service.fifaRanking(confederation, search);
    }

    /** UEFA Kulup Katsayisi. */
    @GetMapping("/uefa/clubs")
    public UefaClubRankingResponse uefaClubs(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search) {
        return service.uefaClubRanking(season, country, search);
    }

    /** UEFA Milli Takim Katsayisi. */
    @GetMapping("/uefa/countries")
    public UefaCountryRankingResponse uefaCountries(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String search) {
        return service.uefaCountryRanking(season, search);
    }
}

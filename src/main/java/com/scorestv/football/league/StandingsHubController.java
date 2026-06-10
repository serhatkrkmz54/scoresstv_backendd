package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.web.dto.LeagueHubResponse;
import com.scorestv.football.web.dto.StandingsPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standings (puan durumu) sayfasi public endpoint'leri.
 *
 * <ul>
 *   <li>{@code GET /api/v1/standings/hub?country=&search=&lang=} — Picker.
 *       Tum ligleri ulke bazinda gruplar; {@code hasStandings} bayragi ile
 *       UI hangi ligin verisi var ayirt eder.</li>
 *   <li>{@code GET /api/v1/standings/{leagueSlug}?season=&lang=} — Detail.
 *       Secilen ligin puan durumu (ligde tablo, kupada bracket) +
 *       sezon dropdown + top 20 rating.</li>
 * </ul>
 *
 * <p>Lig sayfasi ({@code /api/v1/leagues/{slug}}) ayrica vardir — onda
 * fixture/top scorers/cards/SEO de bulunur. Standings sayfasi daha hafif
 * ve focused.
 */
@RestController
@RequestMapping("/api/v1/standings")
public class StandingsHubController {

    private final LeagueHubService hubService;
    private final StandingsPageService pageService;

    public StandingsHubController(LeagueHubService hubService,
                                  StandingsPageService pageService) {
        this.hubService = hubService;
        this.pageService = pageService;
    }

    /** Picker — tum ligler, ulke bazinda. */
    @GetMapping("/hub")
    public LeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return hubService.hub(country, search, "tr".equalsIgnoreCase(lang));
    }

    /**
     * Puan durumu detay — secilen ligin standings/bracket + top 20 rating.
     *
     * <p>Slug format: "premier-league-39" / "super-lig-203". Trailing -ID
     * extract edilir.
     */
    @GetMapping("/{leagueSlug}")
    public StandingsPageResponse detail(
            @PathVariable String leagueSlug,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        Long id = SlugUtil.extractLeagueId(leagueSlug);
        if (id == null) {
            throw ApiException.badRequest("Gecersiz lig slug formati: " + leagueSlug);
        }
        return pageService.getById(id, season, "tr".equalsIgnoreCase(lang));
    }
}

package com.scorestv.basketball.web;

import com.scorestv.basketball.BasketballLeagueHubService;
import com.scorestv.basketball.BasketballLeagueTeamsService;
import com.scorestv.basketball.detail.BasketballStandingsPageService;
import com.scorestv.basketball.web.dto.BasketballLeagueHubResponse;
import com.scorestv.basketball.web.dto.BasketballLeagueTeamView;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Basketbol lig public endpoint'leri — onboarding "favori takım seç" akışı
 * için hafif veri. Mobile bunu accordion'a doldurur.
 *
 * <p>URL: {@code GET /api/v1/basketball/leagues/hub} → ülke gruplu lig listesi.
 * <br>URL: {@code GET /api/v1/basketball/leagues/{id}/teams} → lig içi takımlar.
 */
@RestController
@RequestMapping("/api/v1/basketball/leagues")
public class BasketballLeagueController {

    private final BasketballLeagueHubService hubService;
    private final BasketballLeagueTeamsService teamsService;
    private final BasketballStandingsPageService standingsPageService;

    public BasketballLeagueController(BasketballLeagueHubService hubService,
                                      BasketballLeagueTeamsService teamsService,
                                      BasketballStandingsPageService standingsPageService) {
        this.hubService = hubService;
        this.teamsService = teamsService;
        this.standingsPageService = standingsPageService;
    }

    /**
     * Tüm basketbol ligleri, ülke bazında gruplanmış (onboarding accordion).
     *
     * @param country opsiyonel — sadece bu ülke kodu (örn "TR")
     * @param search  opsiyonel — lig adında contains arama
     * @param lang    "tr" veya "en" (default "en")
     */
    @GetMapping("/hub")
    public BasketballLeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return hubService.hub(country, search, "tr".equalsIgnoreCase(lang));
    }

    /**
     * Bir basketbol liginin takım listesi (hafif). Sezon verilmezse ligin
     * {@code currentSeason}'u kullanılır.
     */
    @GetMapping("/{id}/teams")
    public List<BasketballLeagueTeamView> teamsByLeague(
            @PathVariable Long id,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return teamsService.getTeams(id, season, "tr".equalsIgnoreCase(lang));
    }

    /**
     * B-Faz6: Lig puan durumu sayfasi (slug bazli).
     * <p>URL: {@code GET /api/v1/basketball/leagues/{slug}/standings?season=...&lang=tr}
     *
     * <p>Slug formati: {@code {lig-adi}-{ligId}} (orn. "nba-12"). Sezon
     * verilmezse default = en yeni dolu sezon. {@code lang} dropdown isimlerini
     * etkiler.
     */
    @GetMapping("/{slug}/standings")
    public BasketballStandingsPageResponse standings(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return standingsPageService.getBySlug(slug, season, "tr".equalsIgnoreCase(lang));
    }

    /**
     * Standings force refresh — mobile pull-to-refresh icin.
     * Cache evict + sync + taze veri doner.
     */
    @PostMapping("/{slug}/standings/refresh")
    public BasketballStandingsPageResponse refreshStandings(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return standingsPageService.forceRefresh(slug, season, "tr".equalsIgnoreCase(lang));
    }
}

package com.scorestv.volleyball.web;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.volleyball.VolleyballLeagueHubService;
import com.scorestv.volleyball.VolleyballLeagueTeamsService;
import com.scorestv.volleyball.VolleyballSeasonNormalizer;
import com.scorestv.volleyball.detail.VolleyballStandingsPageService;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.seo.VolleyballLeagueDetailSeoBuilder;
import com.scorestv.volleyball.web.dto.VolleyballLeagueHubResponse;
import com.scorestv.volleyball.web.dto.VolleyballLeagueSeoResponse;
import com.scorestv.volleyball.web.dto.VolleyballLeagueTeamView;
import com.scorestv.volleyball.web.dto.VolleyballStandingsPageResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Voleybol lig public endpoint'leri — onboarding "favori takim sec" akisi +
 * lig puan durumu sayfasi.
 *
 * <ul>
 *   <li>{@code GET /api/v1/volleyball/leagues/hub} — ulke gruplu lig listesi.</li>
 *   <li>{@code GET /api/v1/volleyball/leagues/{id}/teams} — lig ici takimlar.</li>
 *   <li>{@code GET /api/v1/volleyball/leagues/{slug}/standings} — puan durumu.</li>
 *   <li>{@code POST /api/v1/volleyball/leagues/{slug}/standings/refresh} — yenile.</li>
 *   <li>{@code GET /api/v1/volleyball/leagues/{slug}/seo} — lig SEO paketi.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/volleyball/leagues")
public class VolleyballLeagueController {

    private final VolleyballLeagueHubService hubService;
    private final VolleyballLeagueTeamsService teamsService;
    private final VolleyballStandingsPageService standingsPageService;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballLeagueDetailSeoBuilder seoBuilder;

    public VolleyballLeagueController(VolleyballLeagueHubService hubService,
                                      VolleyballLeagueTeamsService teamsService,
                                      VolleyballStandingsPageService standingsPageService,
                                      VolleyballLeagueRepository leagueRepo,
                                      VolleyballLeagueDetailSeoBuilder seoBuilder) {
        this.hubService = hubService;
        this.teamsService = teamsService;
        this.standingsPageService = standingsPageService;
        this.leagueRepo = leagueRepo;
        this.seoBuilder = seoBuilder;
    }

    @GetMapping("/hub")
    public VolleyballLeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return hubService.hub(country, search, "tr".equalsIgnoreCase(lang));
    }

    @GetMapping("/{id}/teams")
    public List<VolleyballLeagueTeamView> teamsByLeague(
            @PathVariable Long id,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return teamsService.getTeams(id, season, "tr".equalsIgnoreCase(lang));
    }

    @GetMapping("/{slug}/standings")
    public VolleyballStandingsPageResponse standings(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return standingsPageService.getBySlug(slug, season, "tr".equalsIgnoreCase(lang));
    }

    @PostMapping("/{slug}/standings/refresh")
    public VolleyballStandingsPageResponse refreshStandings(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return standingsPageService.forceRefresh(slug, season, "tr".equalsIgnoreCase(lang));
    }

    /**
     * Lig detay sayfasi SEO paketi — OpenGraph + Twitter Card + JSON-LD +
     * hreflang. Web SSR sayfa head'ini bu yanitla doldurur.
     *
     * <p>URL: {@code GET /api/v1/volleyball/leagues/{slug}/seo?season=...&lang=tr}.
     * Slug formati: {@code {lig-adi}-{ligId}}. Sezon verilmezse ligin
     * {@code currentSeason}'u kullanilir.
     */
    @GetMapping("/{slug}/seo")
    @Transactional(readOnly = true)
    public VolleyballLeagueSeoResponse seo(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) {
            VolleyballLeague bySlug = leagueRepo.findBySlug(slug).orElse(null);
            leagueId = bySlug != null ? bySlug.getId() : null;
        }
        if (leagueId == null) throw ApiException.notFound("Lig bulunamadi");
        VolleyballLeague league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi"));

        String selectedSeason = (season != null && !season.isBlank())
                ? season : league.getCurrentSeason();
        if (selectedSeason != null) {
            selectedSeason = VolleyballSeasonNormalizer.normalize(
                    selectedSeason, league.getSeasonsJson());
        }
        return seoBuilder.build(league, selectedSeason, lang);
    }
}

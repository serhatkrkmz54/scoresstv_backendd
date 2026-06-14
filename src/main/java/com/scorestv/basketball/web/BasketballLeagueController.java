package com.scorestv.basketball.web;

import com.scorestv.basketball.BasketballLeagueHubService;
import com.scorestv.basketball.BasketballLeagueTeamsService;
import com.scorestv.basketball.detail.BasketballLeagueDetailService;
import com.scorestv.basketball.detail.BasketballLeagueDetailLazySync;
import com.scorestv.basketball.detail.BasketballStandingsPageService;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse;
import com.scorestv.basketball.web.dto.BasketballLeagueHubResponse;
import com.scorestv.basketball.web.dto.BasketballLeagueTeamView;
import com.scorestv.basketball.web.dto.BasketballStandingsPageResponse;
import com.scorestv.common.SlugUtil;
import org.springframework.cache.CacheManager;
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
    private final BasketballLeagueDetailService leagueDetailService;
    private final BasketballLeagueDetailLazySync leagueDetailLazySync;
    private final CacheManager cacheManager;

    public BasketballLeagueController(BasketballLeagueHubService hubService,
                                      BasketballLeagueTeamsService teamsService,
                                      BasketballStandingsPageService standingsPageService,
                                      BasketballLeagueDetailService leagueDetailService,
                                      BasketballLeagueDetailLazySync leagueDetailLazySync,
                                      CacheManager cacheManager) {
        this.hubService = hubService;
        this.teamsService = teamsService;
        this.standingsPageService = standingsPageService;
        this.leagueDetailService = leagueDetailService;
        this.leagueDetailLazySync = leagueDetailLazySync;
        this.cacheManager = cacheManager;
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

    /**
     * C-Faz2: Lig detay sayfasi — overview + sezonlar + standings + son maclar
     * + yaklasan maclar + 3 kategori top players + SEO.
     *
     * <p>URL: {@code GET /api/v1/basketball/leagues/{slug}/detail?season=...&lang=tr}
     *
     * <p>Slug formati: {@code {lig-adi}-{ligId}} (orn. "nba-12"). Sezon
     * verilmezse ligin {@code currentSeason}'u kullanilir. Locale TR/EN.
     *
     * <p>Cache: 1 saat (basketballLeagueDetail). Lazy sync background async
     * — kullanici beklemez, eski veri doner, bir sonraki cagri taze.
     */
    @GetMapping("/{slug}/detail")
    public BasketballLeagueDetailResponse detail(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return leagueDetailService.getDetail(slug, season, lang);
    }

    /**
     * Lig detay force refresh — pull-to-refresh. Cache evict + async refresh
     * tetikle + cache miss ile yeniden doldur.
     */
    @PostMapping("/{slug}/detail/refresh")
    public BasketballLeagueDetailResponse refreshDetail(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId != null) {
            // Cache evict (3 lang varyantini da temizle)
            var cache = cacheManager.getCache("basketballLeagueDetail");
            if (cache != null) {
                cache.evict(slug + ":" + (season != null ? season : "current") + ":tr");
                cache.evict(slug + ":" + (season != null ? season : "current") + ":en");
            }
            leagueDetailLazySync.forceRefresh(leagueId, season);
        }
        return leagueDetailService.getDetail(slug, season, lang);
    }
}

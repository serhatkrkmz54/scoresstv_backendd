package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.web.dto.LeagueDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lig detay sayfasi public endpoint'i.
 *
 * <p>URL semasi: {@code /api/v1/leagues/{slug}} — slug formati
 * {@code {lig-adi}-{ligId}}. Frontend rotalari TR ve EN icin farkli:
 * <ul>
 *   <li>TR: {@code /lig/{tr-slug}-{id}}</li>
 *   <li>EN: {@code /league/{en-slug}-{id}}</li>
 * </ul>
 * Iki frontend yolu da bu tek backend endpoint'ini cagirir; ?lang= param
 * dile karar verir.
 *
 * <p>?season= param secili sezonu belirler — verilmezse ligin current sezonu.
 */
@RestController
@RequestMapping("/api/v1/leagues")
public class PublicLeagueController {

    private final LeagueDetailService service;
    private final LeagueTeamsService teamsService;

    public PublicLeagueController(LeagueDetailService service,
                                  LeagueTeamsService teamsService) {
        this.service = service;
        this.teamsService = teamsService;
    }

    @GetMapping("/{slug:[a-z0-9-]+}")
    public LeagueDetailResponse bySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        Long id = SlugUtil.extractLeagueId(slug);
        if (id == null) {
            throw ApiException.notFound("Lig bulunamadi: gecersiz slug.");
        }
        return service.getById(id, season, "tr".equalsIgnoreCase(lang), refresh);
    }

    /**
     * Hafif takim listesi — onboarding (favori takim secimi) gibi ekranlar
     * icin. Sadece id+ad+logo+kisaltma doner.
     *
     * <p>URL: {@code /api/v1/leagues/{slug}/teams?season=&lang=}.
     */
    @GetMapping("/{slug:[a-z0-9-]+}/teams")
    public java.util.List<com.scorestv.football.web.dto.LeagueTeamView> teamsBySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        Long id = SlugUtil.extractLeagueId(slug);
        if (id == null) {
            throw ApiException.notFound("Lig bulunamadi: gecersiz slug.");
        }
        return teamsService.getTeams(id, season, "tr".equalsIgnoreCase(lang));
    }
}

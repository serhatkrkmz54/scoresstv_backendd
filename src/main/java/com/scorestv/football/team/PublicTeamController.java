package com.scorestv.football.team;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.web.dto.PopularTeamView;
import com.scorestv.football.web.dto.TeamDetailResponse;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Takim detay sayfasi public endpoint'i.
 *
 * <p>URL semasi: {@code /api/v1/teams/{slug}} — slug formati
 * {@code {takim-adi}-{teamId}}. Frontend rotalari TR ve EN icin farkli:
 * <ul>
 *   <li>TR: {@code /takim/{slug}-{id}}</li>
 *   <li>EN: {@code /team/{slug}-{id}}</li>
 * </ul>
 * Iki frontend yolu da bu tek backend endpoint'ini cagirir; ?lang= param
 * dile karar verir.
 *
 * <p>?season= param secili sezonu belirler — verilmezse takimin DB'deki en
 * son (mevcut) sezonu.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class PublicTeamController {

    private final TeamDetailService service;
    private final PopularTeamsService popularTeamsService;

    public PublicTeamController(TeamDetailService service,
                               PopularTeamsService popularTeamsService) {
        this.service = service;
        this.popularTeamsService = popularTeamsService;
    }

    /**
     * Sol ray "Ülkeler" (milli takımlar) listesi — config'ten, elle seçilmiş.
     * "/popular" literal yolu "/{slug}" pattern'ından önce eşleşir.
     */
    @GetMapping("/popular")
    public List<PopularTeamView> popular(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return popularTeamsService.getPopular("tr".equalsIgnoreCase(lang));
    }

    @GetMapping("/{slug:[a-z0-9-]+}")
    public TeamDetailResponse bySlug(
            @PathVariable String slug,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        Long id = SlugUtil.extractTeamId(slug);
        if (id == null) {
            throw ApiException.notFound("Takim bulunamadi: gecersiz slug.");
        }
        return service.getById(id, season, "tr".equalsIgnoreCase(lang));
    }
}

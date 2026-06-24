package com.scorestv.mobile.web;

import com.scorestv.volleyball.VolleyballLeagueHubService;
import com.scorestv.volleyball.web.dto.VolleyballLeagueHubResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile-ozel voleybol lig endpoint'leri. Basketbolun
 * {@link MobileBasketballLeagueController}'i ile ayni pattern — onboarding
 * "favori takim" akisinda kullanilir.
 *
 * <p>URL: {@code GET /api/v1/mobile/volleyball/leagues/hub?lang=&country=&search=}
 */
@RestController
@RequestMapping("/api/v1/mobile/volleyball/leagues")
public class MobileVolleyballLeagueController {

    private final VolleyballLeagueHubService hubService;

    public MobileVolleyballLeagueController(VolleyballLeagueHubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/hub")
    public VolleyballLeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return hubService.hub(country, search, "tr".equalsIgnoreCase(lang));
    }
}

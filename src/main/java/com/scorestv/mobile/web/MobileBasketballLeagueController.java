package com.scorestv.mobile.web;

import com.scorestv.basketball.BasketballLeagueHubService;
import com.scorestv.basketball.web.dto.BasketballLeagueHubResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile-özel basketbol lig endpoint'leri. Futbolun
 * {@link MobileLeagueController}'ı ile aynı pattern — mobile onboarding
 * "favori takım" akışında kullanılır.
 *
 * <p>URL: {@code GET /api/v1/mobile/basketball/leagues/hub?lang=&country=&search=}
 *
 * <p>Şu an futboldaki "Cup hariçle" gibi mobile-özel filtre yok; basketbol
 * lig listesi olduğu gibi döner. İleride mobile-özel filtre gerekirse
 * burada eklenir (web/admin etkilenmez).
 */
@RestController
@RequestMapping("/api/v1/mobile/basketball/leagues")
public class MobileBasketballLeagueController {

    private final BasketballLeagueHubService hubService;

    public MobileBasketballLeagueController(BasketballLeagueHubService hubService) {
        this.hubService = hubService;
    }

    /** Mobile basketbol lig hub'i — şimdilik direkt servis çıktısı. */
    @GetMapping("/hub")
    public BasketballLeagueHubResponse hub(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "tr") String lang) {
        return hubService.hub(country, search, "tr".equalsIgnoreCase(lang));
    }
}

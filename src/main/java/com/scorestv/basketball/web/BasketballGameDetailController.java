package com.scorestv.basketball.web;

import com.scorestv.basketball.detail.BasketballGameDetailService;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse;
import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basketbol mac detayi public endpoint'leri.
 *
 * <ul>
 *   <li>{@code GET /api/v1/basketball/games/detail/{slug}?lang=tr} — tam detay
 *       (cached 30sn)</li>
 *   <li>{@code GET /api/v1/basketball/games/detail/{slug}/seo?lang=tr} — sadece
 *       SEO paketi (web SSR icin)</li>
 *   <li>{@code POST /api/v1/basketball/games/detail/{slug}/refresh?lang=tr} —
 *       force refresh: cache evict + lazy sync tetikle, taze veri doner</li>
 * </ul>
 *
 * <p>Slug formati: {@code home-vs-away-{gameId}} — sondan id cikarilir,
 * isim degisikliklerine dayanikli (eski slug yine cozulur).
 *
 * <p>Mevcut {@link BasketballGameController} ({@code /api/v1/basketball/games})
 * fixstur liste/canli/by-id sunar; bu controller detay sayfasina ozeldir.
 * Path'ler {@code /detail/} on ekiyle catismayi onler.
 */
@RestController
@RequestMapping("/api/v1/basketball/games/detail")
public class BasketballGameDetailController {

    private final BasketballGameDetailService service;

    public BasketballGameDetailController(BasketballGameDetailService service) {
        this.service = service;
    }

    /** Tam detay — cached. */
    @GetMapping("/{slug}")
    public BasketballGameDetailResponse get(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw new ApiException(404, "Mac bulunamadi");
        boolean turkish = "tr".equalsIgnoreCase(lang);
        return service.getById(id, turkish);
    }

    /** Sadece SEO paketi — web SSR'da head meta uretmek icin hizli ucun. */
    @GetMapping("/{slug}/seo")
    public BasketballGameDetailResponse.SeoBundle seo(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw new ApiException(404, "Mac bulunamadi");
        boolean turkish = "tr".equalsIgnoreCase(lang);
        return service.getSeoById(id, turkish);
    }

    /**
     * Force refresh — cache'i evict eder, lazy sync tetikler, taze veri doner.
     * Mobile pull-to-refresh ve "Yenile" butonu icin.
     */
    @PostMapping("/{slug}/refresh")
    public BasketballGameDetailResponse refresh(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw new ApiException(404, "Mac bulunamadi");
        boolean turkish = "tr".equalsIgnoreCase(lang);
        return service.getById(id, turkish, true);
    }
}

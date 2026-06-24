package com.scorestv.volleyball.web;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.volleyball.detail.VolleyballGameDetailService;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voleybol mac detayi public endpoint'leri.
 *
 * <ul>
 *   <li>{@code GET /api/v1/volleyball/games/detail/{slug}?lang=tr} — tam detay (cached)</li>
 *   <li>{@code GET /api/v1/volleyball/games/detail/{slug}/seo?lang=tr} — sadece
 *       SEO paketi (web SSR icin)</li>
 *   <li>{@code POST /api/v1/volleyball/games/detail/{slug}/refresh?lang=tr} — force refresh</li>
 * </ul>
 *
 * <p>Slug formati: {@code home-vs-away-{gameId}} — sondan id cikarilir.
 */
@RestController
@RequestMapping("/api/v1/volleyball/games/detail")
public class VolleyballGameDetailController {

    private final VolleyballGameDetailService service;

    public VolleyballGameDetailController(VolleyballGameDetailService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    public VolleyballGameDetailResponse get(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw ApiException.notFound("Mac bulunamadi");
        return service.getById(id, "tr".equalsIgnoreCase(lang));
    }

    /** Sadece SEO paketi — web SSR'da head meta uretmek icin hizli ucun. */
    @GetMapping("/{slug}/seo")
    public VolleyballGameDetailResponse.SeoBundle seo(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw ApiException.notFound("Mac bulunamadi");
        return service.getSeoById(id, "tr".equalsIgnoreCase(lang));
    }

    @PostMapping("/{slug}/refresh")
    public VolleyballGameDetailResponse refresh(
            @PathVariable String slug,
            @RequestParam(name = "lang", required = false, defaultValue = "tr") String lang) {
        Long id = SlugUtil.extractGameId(slug);
        if (id == null) throw ApiException.notFound("Mac bulunamadi");
        return service.getById(id, "tr".equalsIgnoreCase(lang), true);
    }
}

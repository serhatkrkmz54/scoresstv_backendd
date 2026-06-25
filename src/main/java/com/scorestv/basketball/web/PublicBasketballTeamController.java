package com.scorestv.basketball.web;

import com.scorestv.basketball.detail.BasketballTeamDetailService;
import com.scorestv.basketball.seo.BasketballTeamDetailSeoBuilder;
import com.scorestv.basketball.web.dto.BasketballPopularTeamView;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Basketbol takim detay public endpoint'i.
 *
 * <p>URL: {@code GET /api/v1/basketball/teams/popular} — sol ray popüler takimlar.
 * <br>URL: {@code GET /api/v1/basketball/teams/{slug}} — tam detay.
 * <br>URL: {@code POST /api/v1/basketball/teams/{slug}/refresh} — pull-to-refresh.
 * <br>URL: {@code GET  /api/v1/basketball/teams/{slug}/seo} — SEO paketi (OG/JSON-LD).
 *
 * <p>Slug format: {@code {takim-adi}-{teamId}} (orn. "fenerbahce-145").
 * Sezon verilmezse default = junction tablosunda en yeni sezon.
 */
@RestController
@RequestMapping("/api/v1/basketball/teams")
public class PublicBasketballTeamController {

    private final BasketballTeamDetailService detailService;
    private final BasketballTeamDetailSeoBuilder seoBuilder;
    private final BasketballPopularTeamsService popularTeamsService;

    public PublicBasketballTeamController(BasketballTeamDetailService detailService,
                                            BasketballTeamDetailSeoBuilder seoBuilder,
                                            BasketballPopularTeamsService popularTeamsService) {
        this.detailService = detailService;
        this.seoBuilder = seoBuilder;
        this.popularTeamsService = popularTeamsService;
    }

    /**
     * Sol ray "Popüler Takimlar" listesi (config'ten, elle seçilmiş).
     * Not: "/popular" literal yolu, "/{slug}" pattern'indan ÖNCE eşleşmesi
     * için ilk sırada tanımlandı (futbol PublicTeamController ile aynı gotcha).
     *
     * @param lang "tr" → Türkçe ad/slug; aksi halde "en"
     */
    @GetMapping("/popular")
    public List<BasketballPopularTeamView> popular(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return popularTeamsService.getPopular("tr".equalsIgnoreCase(lang));
    }

    /** Takim detay sayfasi yanit DTO'su. */
    @GetMapping("/{slug}")
    public BasketballTeamDetailResponse getDetail(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return detailService.getBySlug(slug, season, "tr".equalsIgnoreCase(lang));
    }

    /** Pull-to-refresh — cache evict + lazy sync paralel + taze yanit. */
    @PostMapping("/{slug}/refresh")
    public BasketballTeamDetailResponse refresh(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return detailService.forceRefresh(slug, season, "tr".equalsIgnoreCase(lang));
    }

    /**
     * SEO paketi — OpenGraph + Twitter Card + JSON-LD + hreflang. Mobile bunu
     * cekmez; SSR/HTML rendering tarafi kullanir. Frontend Next.js sayfa
     * head'ini bu yanitla doldurur.
     */
    @GetMapping("/{slug}/seo")
    public Map<String, Object> seo(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        var detail = detailService.getBySlug(slug, season, "tr".equalsIgnoreCase(lang));
        return seoBuilder.build(detail, "tr".equalsIgnoreCase(lang));
    }
}

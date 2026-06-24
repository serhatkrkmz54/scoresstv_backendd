package com.scorestv.volleyball.web;

import com.scorestv.volleyball.detail.VolleyballTeamDetailService;
import com.scorestv.volleyball.seo.VolleyballTeamDetailSeoBuilder;
import com.scorestv.volleyball.web.dto.VolleyballTeamDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Voleybol takim detay public endpoint'i.
 *
 * <p>URL: {@code GET /api/v1/volleyball/teams/{slug}} — tam detay.
 * <br>URL: {@code POST /api/v1/volleyball/teams/{slug}/refresh} — pull-to-refresh.
 * <br>URL: {@code GET  /api/v1/volleyball/teams/{slug}/seo} — SEO paketi (OG/JSON-LD).
 *
 * <p>Slug format: {@code {takim-adi}-{teamId}}.
 */
@RestController
@RequestMapping("/api/v1/volleyball/teams")
public class PublicVolleyballTeamController {

    private final VolleyballTeamDetailService detailService;
    private final VolleyballTeamDetailSeoBuilder seoBuilder;

    public PublicVolleyballTeamController(VolleyballTeamDetailService detailService,
                                            VolleyballTeamDetailSeoBuilder seoBuilder) {
        this.detailService = detailService;
        this.seoBuilder = seoBuilder;
    }

    @GetMapping("/{slug}")
    public VolleyballTeamDetailResponse getDetail(
            @PathVariable String slug,
            @RequestParam(required = false) String season,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return detailService.getBySlug(slug, season, "tr".equalsIgnoreCase(lang));
    }

    @PostMapping("/{slug}/refresh")
    public VolleyballTeamDetailResponse refresh(
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

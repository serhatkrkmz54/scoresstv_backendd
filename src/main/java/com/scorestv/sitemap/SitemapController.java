package com.scorestv.sitemap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Sitemap uretimi icin public listeleme uclari. Frontend (Next) bunlari
 * generateSitemaps ile cagirip XML'leri uretir.
 *
 * <p>{@code GET /api/v1/sitemap/counts} → {teams, players, leagues} sayilari.
 * <p>{@code GET /api/v1/sitemap/{type}?page=&size=} → path + lastmod listesi.
 */
@RestController
@RequestMapping("/api/v1/sitemap")
public class SitemapController {

    private static final int MAX_SIZE = 20000;

    private final SitemapService service;

    public SitemapController(SitemapService service) {
        this.service = service;
    }

    @GetMapping("/counts")
    public Map<String, Long> counts() {
        return service.counts();
    }

    @GetMapping("/{type}")
    public List<SitemapEntry> page(
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10000") int size) {
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);
        return service.page(type, Math.max(0, page), safeSize);
    }
}

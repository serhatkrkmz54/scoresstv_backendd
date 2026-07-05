package com.scorestv.news;

import com.scorestv.news.dto.NewsDetail;
import com.scorestv.news.dto.NewsPageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Haber (news) public endpoint'leri — kimlik gerektirmez. Yalniz yayinda
 * (PUBLISHED) haberleri okur. SecurityConfig'te GET /api/v1/news/** permitAll.
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService service;

    public NewsController(NewsService service) {
        this.service = service;
    }

    /**
     * Yayinda haber listesi. lang zorunludur (tr|en). Opsiyonel filtreler:
     * category, sport, teamId, leagueId, featured. teamId/leagueId verilirse
     * o varliga bagli haberler doner.
     */
    @GetMapping
    public NewsPageResponse list(
            @RequestParam(defaultValue = "tr") String lang,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NewsCategory category,
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(required = false) Boolean featured) {
        if (teamId != null) {
            return service.listPublishedByTeam(teamId, lang, page, size);
        }
        if (leagueId != null) {
            return service.listPublishedByLeague(leagueId, lang, page, size);
        }
        return service.listPublished(lang, page, size, category, sport, featured);
    }

    /** Yayinda haber detayi (slug ile). Goruntuleme sayisini artirir. */
    @GetMapping("/{slug}")
    public NewsDetail detail(@PathVariable String slug) {
        return service.getPublishedBySlug(slug);
    }
}

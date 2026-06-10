package com.scorestv.football.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.web.dto.MatchDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maç detay sayfası için public endpoint.
 *
 * <p>URL şeması: {@code /api/v1/fixtures/{slug}} — slug formatı
 * {@code ev-vs-deplasman-{fixtureId}}. Sondaki sayı maç id'sidir; takım adı
 * değişse bile eski slug'lar doğru maça çözülür (bkz. {@link SlugUtil#extractFixtureId(String)}).
 *
 * <p>Path regex {@code [a-z0-9-]+} sayesinde {@code /dates} ve {@code /live}
 * gibi mevcut alt yollarla çakışmaz — Spring zaten somut yolları önce eşler.
 */
@RestController
@RequestMapping("/api/v1/fixtures")
public class PublicMatchController {

    private final MatchDetailService matchDetailService;

    public PublicMatchController(MatchDetailService matchDetailService) {
        this.matchDetailService = matchDetailService;
    }

    /**
     * Slug'dan maç detayını çözer.
     *
     * @param slug "ev-vs-deplasman-{id}" biçiminde URL slug'ı
     * @param lang "tr" → Türkçe adlar + SEO metinleri; aksi halde "en"
     * @param country kullanici ulke kodu (TR/GB/US...) — TV yayin filtresi
     *                için. Null/bos → "TR" varsayilir. Frontend Cloudflare
     *                {@code CF-IPCountry} veya navigator.language ile tespit
     *                eder ve query param olarak iletir.
     * @param refresh true → Redis cache evict + lazy sync debounce reset.
     *                Mobile pull-to-refresh / TopBar refresh butonu icin.
     *                Default false — normal cache'li akis.
     */
    @GetMapping("/{slug:[a-z0-9-]+}")
    public MatchDetailResponse bySlug(@PathVariable String slug,
                                     @RequestParam(required = false, defaultValue = "en") String lang,
                                     @RequestParam(required = false) String country,
                                     @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        Long id = SlugUtil.extractFixtureId(slug);
        if (id == null) {
            throw ApiException.notFound("Maç bulunamadı: geçersiz slug.");
        }
        return matchDetailService.getById(id, country, "tr".equalsIgnoreCase(lang), refresh);
    }
}

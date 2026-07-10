package com.scorestv.football.insight;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "AI Analiz" public endpoint'i: {@code GET /api/v1/fixtures/{slug}/insight}.
 *
 * <p>{@code /api/v1/fixtures/**} zaten SecurityConfig'de permitAll — ek izin yok.
 * Path regex {@code [a-z0-9-]+} sondaki {@code /insight} ile çakışmaz.
 */
@RestController
@RequestMapping("/api/v1/fixtures")
public class MatchInsightController {

    private final FixtureRepository fixtureRepository;
    private final MatchInsightService insightService;

    public MatchInsightController(FixtureRepository fixtureRepository,
                                  MatchInsightService insightService) {
        this.fixtureRepository = fixtureRepository;
        this.insightService = insightService;
    }

    @GetMapping("/{slug:[a-z0-9-]+}/insight")
    public MatchInsightResponse insight(
            @PathVariable String slug,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        Long id = SlugUtil.extractFixtureId(slug);
        if (id == null) {
            throw ApiException.notFound("Maç bulunamadı: geçersiz slug.");
        }
        Fixture fixture = fixtureRepository.findByIdWithTeams(id)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı."));
        return insightService.forFixture(fixture, "tr".equalsIgnoreCase(lang));
    }
}

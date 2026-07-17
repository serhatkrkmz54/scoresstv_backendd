package com.scorestv.football.insight;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Analiz isabet karnesi — public. {@code GET /api/v1/ai/performance}.
 * SecurityConfig'de permitAll. Aylık/yıllık/tüm-zaman + son 12 ay kırılımı.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiPerformanceController {

    private final AiPerformanceService service;

    public AiPerformanceController(AiPerformanceService service) {
        this.service = service;
    }

    @GetMapping("/performance")
    public AiPerformanceView performance() {
        return service.performance();
    }
}

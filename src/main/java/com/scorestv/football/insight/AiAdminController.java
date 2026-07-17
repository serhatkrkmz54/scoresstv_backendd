package com.scorestv.football.insight;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI tahmin yönetimi — yalnız ADMIN. Geçmişi geriye doldurma (backfill) için.
 * {@code /api/v1/admin/**} zaten authenticated; ek @PreAuthorize ile ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
public class AiAdminController {

    private final AiPredictionRecorder recorder;

    public AiAdminController(AiPredictionRecorder recorder) {
        this.recorder = recorder;
    }

    /** Son {@code days} gün (1..365) bitmiş covered maçları yaklaşık doldur. */
    @PostMapping("/backfill")
    public Map<String, Object> backfill(@RequestParam(defaultValue = "45") int days) {
        final int recorded = recorder.backfill(days);
        return Map.of("recorded", recorded, "days", Math.max(1, Math.min(days, 365)));
    }
}

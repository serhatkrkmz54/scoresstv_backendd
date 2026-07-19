package com.scorestv.stats;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel "Uygulama Istatistikleri" ucu. {@code /api/v1/admin/**} SecurityConfig'te
 * authenticated; burada EDITOR/ADMIN ile gatelenir.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AppStatsController {

    private final AppStatsService service;

    public AppStatsController(AppStatsService service) {
        this.service = service;
    }

    @GetMapping("/app")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public AppStats app() {
        return service.compute();
    }
}

package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in tek bir maçın istatistiklerini elle senkronlamasını sağlayan endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class FixtureStatisticsSyncController {

    private final FixtureStatisticsSyncService syncService;

    public FixtureStatisticsSyncController(FixtureStatisticsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/statistics/sync")
    public FixtureStatisticsSyncResult syncStatistics(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

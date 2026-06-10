package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in tek bir maçın kadrolarını elle senkronlamasını sağlayan endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class FixtureLineupsSyncController {

    private final FixtureLineupsSyncService syncService;

    public FixtureLineupsSyncController(FixtureLineupsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/lineups/sync")
    public FixtureLineupsSyncResult syncLineups(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN için maç-bazlı oyuncu istatistik senkron tetikleyicisi. */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class FixturePlayerStatsSyncController {

    private final FixturePlayerStatsSyncService syncService;

    public FixturePlayerStatsSyncController(FixturePlayerStatsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/players/sync")
    public FixturePlayerStatsSyncResult syncPlayers(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

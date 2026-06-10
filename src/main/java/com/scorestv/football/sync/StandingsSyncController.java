package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in bir lig + sezonun puan durumunu elle senkronlamasını sağlayan endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/leagues")
@PreAuthorize("hasRole('ADMIN')")
public class StandingsSyncController {

    private final StandingsSyncService syncService;

    public StandingsSyncController(StandingsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{leagueId}/standings/sync")
    public StandingsSyncResult sync(@PathVariable Long leagueId,
                                    @RequestParam Integer season) {
        return syncService.sync(leagueId, season);
    }
}

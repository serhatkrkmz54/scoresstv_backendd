package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN için maç bazlı sakatlık senkron tetikleyicisi. */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class InjuriesSyncController {

    private final InjuriesSyncService syncService;

    public InjuriesSyncController(InjuriesSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/injuries/sync")
    public InjuriesSyncResult sync(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

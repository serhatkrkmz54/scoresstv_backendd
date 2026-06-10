package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in tek bir maçın olaylarını elle senkronlamasını sağlayan endpoint.
 * Debug/test için ve canlı job kapalıyken manuel tetikleme için.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class FixtureEventsSyncController {

    private final FixtureEventsSyncService syncService;

    public FixtureEventsSyncController(FixtureEventsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/events/sync")
    public FixtureEventsSyncResult syncEvents(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

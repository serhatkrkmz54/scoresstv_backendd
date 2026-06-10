package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN için maç bazlı tahmin senkron tetikleyicisi. */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class PredictionsSyncController {

    private final PredictionsSyncService syncService;

    public PredictionsSyncController(PredictionsSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/predictions/sync")
    public PredictionsSyncResult sync(@PathVariable Long fixtureId) {
        return syncService.sync(fixtureId);
    }
}

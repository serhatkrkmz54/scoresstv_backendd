package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in bir maçın H2H (head-to-head) verisini elle senkronlamasını sağlayan
 * endpoint. {@code last} (varsayılan 10) son N karşılaşmayı çeker.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/fixtures")
@PreAuthorize("hasRole('ADMIN')")
public class H2hSyncController {

    private final H2hSyncService syncService;

    public H2hSyncController(H2hSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/{fixtureId}/h2h/sync")
    public H2hSyncResult sync(@PathVariable Long fixtureId,
                              @RequestParam(defaultValue = "10") int last) {
        return syncService.syncForFixture(fixtureId, last);
    }
}

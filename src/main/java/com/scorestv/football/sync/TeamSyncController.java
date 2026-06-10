package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN'in takım senkronunu elle tetiklemesi için endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football")
@PreAuthorize("hasRole('ADMIN')")
public class TeamSyncController {

    private final TeamSyncService syncService;

    public TeamSyncController(TeamSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Takım senkronunu elle çalıştırır.
     *
     * @param leagueId verilirse yalnızca o ligin tüm sezonları senkronlanır;
     *                 verilmezse tüm liglerin tüm sezonları (tam arşiv)
     */
    @PostMapping("/teams/sync")
    public TeamSyncResult syncTeams(@RequestParam(required = false) Long leagueId) {
        if (leagueId != null) {
            int upserted = syncService.syncOneLeague(leagueId);
            return new TeamSyncResult(1, 0, upserted);
        }
        return syncService.syncAll();
    }
}

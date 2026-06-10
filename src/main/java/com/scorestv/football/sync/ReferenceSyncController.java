package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ADMIN'in referans veri senkronunu (ülkeler / ligler / sezonlar) elle
 * tetiklemesi için endpoint'ler.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/reference")
@PreAuthorize("hasRole('ADMIN')")
public class ReferenceSyncController {

    private final ReferenceSyncService syncService;

    public ReferenceSyncController(ReferenceSyncService syncService) {
        this.syncService = syncService;
    }

    /** Yalnızca ülkeleri senkronlar ({@code /countries}). */
    @PostMapping("/countries")
    public ReferenceSyncResult syncCountries() {
        return new ReferenceSyncResult(syncService.syncCountries(), 0, 0, 0);
    }

    /** Ligleri ve sezonlarını senkronlar ({@code /leagues}). */
    @PostMapping("/leagues")
    public ReferenceSyncResult syncLeagues() {
        return syncService.syncLeagues();
    }

    /** Ülkeleri + ligleri + sezonları birlikte senkronlar. */
    @PostMapping("/sync")
    public ReferenceSyncResult syncAll() {
        return syncService.syncAll();
    }

    /**
     * Tek bir ligi (ve onun tüm sezonlarını) senkronlar — lig detay sayfası
     * için manuel tetikleme. {@code GET /leagues?id=X} sonucu upsert edilir.
     */
    @PostMapping("/leagues/{id}/sync")
    public Map<String, Object> syncOne(@PathVariable Long id) {
        int seasons = syncService.syncOne(id);
        return Map.of("leagueId", id, "seasonsUpserted", seasons);
    }
}

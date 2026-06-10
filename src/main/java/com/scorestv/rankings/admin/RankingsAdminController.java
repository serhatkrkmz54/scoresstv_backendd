package com.scorestv.rankings.admin;

import com.scorestv.rankings.sync.DailyRankingsJob;
import com.scorestv.rankings.sync.FifaRankingSyncService;
import com.scorestv.rankings.sync.UefaClubRankingSyncService;
import com.scorestv.rankings.sync.UefaCountryRankingSyncService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rankings admin endpoint'leri — manuel tetikleme.
 *
 * <p>Otomatik gunluk job (03:00) zaten cailisir; bu endpointler kotali
 * acil senaryolar veya ilk DB doldurma icin.
 */
@RestController
@RequestMapping("/api/v1/admin/rankings")
@PreAuthorize("hasRole('ADMIN')")
public class RankingsAdminController {

    private final FifaRankingSyncService fifaSync;
    private final UefaClubRankingSyncService uefaClubSync;
    private final UefaCountryRankingSyncService uefaCountrySync;

    public RankingsAdminController(FifaRankingSyncService fifaSync,
                                    UefaClubRankingSyncService uefaClubSync,
                                    UefaCountryRankingSyncService uefaCountrySync) {
        this.fifaSync = fifaSync;
        this.uefaClubSync = uefaClubSync;
        this.uefaCountrySync = uefaCountrySync;
    }

    @PostMapping("/sync-fifa")
    public SyncResult syncFifa() {
        return new SyncResult("FIFA", fifaSync.sync());
    }

    @PostMapping("/sync-uefa-clubs")
    public SyncResult syncUefaClubs(
            @RequestParam(required = false) Integer season) {
        Integer effective = season != null ? season
                : DailyRankingsJob.currentTargetSeasonYear();
        return new SyncResult("UEFA_CLUBS", uefaClubSync.sync(effective));
    }

    @PostMapping("/sync-uefa-countries")
    public SyncResult syncUefaCountries(
            @RequestParam(required = false) Integer season) {
        Integer effective = season != null ? season
                : DailyRankingsJob.currentTargetSeasonYear();
        return new SyncResult("UEFA_COUNTRIES", uefaCountrySync.sync(effective));
    }

    @PostMapping("/sync-all")
    public SyncResult syncAll() {
        int fifa = fifaSync.sync();
        Integer effective = DailyRankingsJob.currentTargetSeasonYear();
        int uefaClub = uefaClubSync.sync(effective);
        int uefaCountry = uefaCountrySync.sync(effective);
        return new SyncResult("ALL",
                fifa + uefaClub + uefaCountry);
    }

    public record SyncResult(String type, int rowsWritten) {}
}

package com.scorestv.volleyball.web;

import com.scorestv.volleyball.VolleyballImageMirrorService;
import com.scorestv.volleyball.VolleyballReferenceService;
import com.scorestv.volleyball.VolleyballStandingsSyncService;
import com.scorestv.volleyball.VolleyballSyncService;
import com.scorestv.volleyball.VolleyballTeamSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADMIN'in voleybol senkronunu elle tetiklemesi icin uclar.
 *
 * <p>{@code scorestv.volleyball.enabled=false} olsa bile calisir — ozellik
 * otomatik acilmadan once tum boru hattini test etmek icin.
 */
@RestController
@RequestMapping("/api/v1/admin/volleyball")
@PreAuthorize("hasRole('ADMIN')")
public class VolleyballAdminController {

    private final VolleyballSyncService sync;
    private final VolleyballReferenceService reference;
    private final VolleyballStandingsSyncService standings;
    private final VolleyballImageMirrorService imageMirror;
    private final VolleyballTeamSyncService teamSync;

    public VolleyballAdminController(VolleyballSyncService sync,
                                     VolleyballReferenceService reference,
                                     VolleyballStandingsSyncService standings,
                                     VolleyballImageMirrorService imageMirror,
                                     VolleyballTeamSyncService teamSync) {
        this.sync = sync;
        this.reference = reference;
        this.standings = standings;
        this.imageMirror = imageMirror;
        this.teamSync = teamSync;
    }

    /** Fikstur senkronu — {@code date} verilirse o gun, yoksa tum +-gun pencere. */
    @PostMapping("/games/sync")
    public Map<String, Object> syncGames(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var result = new LinkedHashMap<String, Object>();
        if (date != null) {
            result.put("date", date.toString());
            result.put("upserted", sync.syncDate(date));
            return result;
        }
        var dates = sync.windowDates();
        int total = 0;
        for (LocalDate d : dates) {
            total += sync.syncDate(d);
        }
        result.put("dates", dates.size());
        result.put("upserted", total);
        return result;
    }

    /** Canli skor senkronu (bugunun maclari). */
    @PostMapping("/games/sync-live")
    public Map<String, Object> syncLive() {
        return Map.of("upserted", sync.syncLive());
    }

    /** Referans seed — ulkeler + ligler (TUM lig katalogu). */
    @PostMapping("/reference/sync")
    public Map<String, Object> syncReference() {
        var result = new LinkedHashMap<String, Object>();
        result.put("countries", reference.syncCountries());
        result.put("leagues", reference.syncLeagues());
        return result;
    }

    /**
     * Takim kadrosu senkronu — {@code leagueId}+{@code season} verilirse o lig,
     * yoksa TUM liglerin currentSeason kadrosu ({@code /teams?league&season}).
     */
    @PostMapping("/teams/sync")
    public Map<String, Object> syncTeams(
            @RequestParam(required = false) Long leagueId,
            @RequestParam(required = false) String season) {
        var result = new LinkedHashMap<String, Object>();
        if (leagueId != null && season != null && !season.isBlank()) {
            result.put("league", leagueId);
            result.put("season", season);
            result.put("teams", teamSync.syncTeamsForLeague(leagueId, season));
        } else {
            result.put("leaguesProcessed", teamSync.syncAllCurrentSeasons());
        }
        return result;
    }

    /**
     * Tek seferlik TAM bootstrap: ulke + lig referansi, ardindan tum liglerin
     * takim kadrosu. Ilk deploy sonrasi "her seyi cek" butonu.
     */
    @PostMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        var result = new LinkedHashMap<String, Object>();
        result.put("countries", reference.syncCountries());
        result.put("leagues", reference.syncLeagues());
        result.put("teamLeaguesProcessed", teamSync.syncAllCurrentSeasons());
        return result;
    }

    /** Standings senkronu — bir lig + sezon. */
    @PostMapping("/standings/sync")
    public Map<String, Object> syncStandings(
            @RequestParam Long leagueId,
            @RequestParam String season) {
        return Map.of("rows", standings.sync(leagueId, season));
    }

    /** Logo/bayrak aynalama — yeni gorselleri CDN'e tasir. */
    @PostMapping("/images/mirror")
    public Map<String, Object> mirrorImages() {
        return Map.of("mirrored", imageMirror.mirrorAll());
    }
}

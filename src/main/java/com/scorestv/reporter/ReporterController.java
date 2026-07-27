package com.scorestv.reporter;

import com.scorestv.common.ApiException;
import com.scorestv.reporter.ReporterDtos.ActionRequest;
import com.scorestv.reporter.ReporterDtos.ApplicationView;
import com.scorestv.reporter.ReporterDtos.ApplyRequest;
import com.scorestv.reporter.ReporterDtos.BroadcastsRequest;
import com.scorestv.reporter.ReporterDtos.BroadcastsView;
import com.scorestv.reporter.ReporterDtos.CreateFixtureRequest;
import com.scorestv.reporter.ReporterDtos.CreateTeamRequest;
import com.scorestv.reporter.ReporterDtos.EventRequest;
import com.scorestv.reporter.ReporterDtos.EventResult;
import com.scorestv.reporter.ReporterDtos.EventView;
import com.scorestv.reporter.ReporterDtos.FixtureView;
import com.scorestv.reporter.ReporterDtos.LineupRequest;
import com.scorestv.reporter.ReporterDtos.LineupView;
import com.scorestv.reporter.ReporterDtos.MeResponse;
import com.scorestv.reporter.ReporterDtos.TeamView;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saha Muhabiri konsolu — web /muhabir sayfası bu uçları kullanır.
 * Giriş zorunlu; lig bazlı yetki atama tablosundan doğrulanır. Yalnız
 * source=manual kayıtlar yazılabilir (servis katmanı mutlak korkuluğu).
 */
@RestController
@RequestMapping("/api/v1/reporter")
public class ReporterController {

    private final ReporterService service;
    private final ReporterMatchDataService matchData;

    public ReporterController(ReporterService service, ReporterMatchDataService matchData) {
        this.service = service;
        this.matchData = matchData;
    }

    private static Long uid(CurrentUser u) {
        if (u == null) throw ApiException.unauthorized("Giriş gerekli.");
        return u.id();
    }

    /** Muhabir genel görünümü: atanmış ligler + başvuru geçmişi. */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal CurrentUser user) {
        return service.me(uid(user));
    }

    /** Muhabirlik başvurusu ("X ligini ben girerim"). */
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView apply(@Valid @RequestBody ApplyRequest req,
                                 @AuthenticationPrincipal CurrentUser user) {
        return service.apply(uid(user), req);
    }

    // ===== Atanmış lig yönetimi =====

    @GetMapping("/leagues/{leagueId}/teams")
    public List<TeamView> teams(@PathVariable Long leagueId,
                                @AuthenticationPrincipal CurrentUser user) {
        return service.listTeams(uid(user), leagueId);
    }

    @PostMapping("/leagues/{leagueId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamView createTeam(@PathVariable Long leagueId,
                               @Valid @RequestBody CreateTeamRequest req,
                               @AuthenticationPrincipal CurrentUser user) {
        return service.createTeam(uid(user), leagueId, req);
    }

    // ===== Logo yükleme (PNG/JPG/WebP, max 2MB) =====

    @PostMapping(value = "/teams/{teamId}/logo",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public TeamView uploadTeamLogo(
            @PathVariable Long teamId,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal CurrentUser user) throws java.io.IOException {
        return service.uploadTeamLogo(uid(user), teamId,
                file.getBytes(), file.getContentType());
    }

    @PostMapping(value = "/leagues/{leagueId}/logo",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReporterDtos.AssignedLeagueView uploadLeagueLogo(
            @PathVariable Long leagueId,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal CurrentUser user) throws java.io.IOException {
        return service.uploadLeagueLogo(uid(user), leagueId,
                file.getBytes(), file.getContentType());
    }

    @GetMapping("/leagues/{leagueId}/fixtures")
    public List<FixtureView> fixtures(@PathVariable Long leagueId,
                                      @AuthenticationPrincipal CurrentUser user) {
        return service.listFixtures(uid(user), leagueId);
    }

    @PostMapping("/leagues/{leagueId}/fixtures")
    @ResponseStatus(HttpStatus.CREATED)
    public FixtureView createFixture(@PathVariable Long leagueId,
                                     @Valid @RequestBody CreateFixtureRequest req,
                                     @AuthenticationPrincipal CurrentUser user) {
        return service.createFixture(uid(user), leagueId, req);
    }

    /** Tekil maç — mobil/web konsol periyodik tazeleme (dakika/skor senkronu). */
    @GetMapping("/fixtures/{fixtureId}")
    public FixtureView fixture(@PathVariable Long fixtureId,
                               @AuthenticationPrincipal CurrentUser user) {
        return service.getFixture(uid(user), fixtureId);
    }

    /** Canlı konsol aksiyonu (START/HT/SECOND_HALF/FINISH/...). */
    @PostMapping("/fixtures/{fixtureId}/actions")
    public FixtureView action(@PathVariable Long fixtureId,
                              @Valid @RequestBody ActionRequest req,
                              @AuthenticationPrincipal CurrentUser user) {
        return service.action(uid(user), fixtureId, req);
    }

    // ===== Maç olayları (gol/kart/değişiklik/VAR) =====

    @GetMapping("/fixtures/{fixtureId}/events")
    public List<EventView> events(@PathVariable Long fixtureId,
                                  @AuthenticationPrincipal CurrentUser user) {
        return matchData.listEvents(uid(user), fixtureId);
    }

    @PostMapping("/fixtures/{fixtureId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResult addEvent(@PathVariable Long fixtureId,
                                @Valid @RequestBody EventRequest req,
                                @AuthenticationPrincipal CurrentUser user) {
        return matchData.addEvent(uid(user), fixtureId, req);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/fixtures/{fixtureId}/events/{eventId}")
    public FixtureView deleteEvent(@PathVariable Long fixtureId,
                                   @PathVariable Long eventId,
                                   @AuthenticationPrincipal CurrentUser user) {
        return matchData.deleteEvent(uid(user), fixtureId, eventId);
    }

    // ===== Kadro =====

    @GetMapping("/fixtures/{fixtureId}/lineups/{side}")
    public LineupView lineup(@PathVariable Long fixtureId,
                             @PathVariable String side,
                             @AuthenticationPrincipal CurrentUser user) {
        return matchData.getLineup(uid(user), fixtureId, side);
    }

    @org.springframework.web.bind.annotation.PutMapping("/fixtures/{fixtureId}/lineups/{side}")
    public LineupView saveLineup(@PathVariable Long fixtureId,
                                 @PathVariable String side,
                                 @Valid @RequestBody LineupRequest req,
                                 @AuthenticationPrincipal CurrentUser user) {
        return matchData.saveLineup(uid(user), fixtureId, side, req);
    }

    // ===== Yayın kanalları =====

    @GetMapping("/fixtures/{fixtureId}/broadcasts")
    public BroadcastsView broadcasts(@PathVariable Long fixtureId,
                                     @AuthenticationPrincipal CurrentUser user) {
        return matchData.getBroadcasts(uid(user), fixtureId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/fixtures/{fixtureId}/broadcasts")
    public BroadcastsView saveBroadcasts(@PathVariable Long fixtureId,
                                         @Valid @RequestBody BroadcastsRequest req,
                                         @AuthenticationPrincipal CurrentUser user) {
        return matchData.saveBroadcasts(uid(user), fixtureId, req.channels());
    }
}

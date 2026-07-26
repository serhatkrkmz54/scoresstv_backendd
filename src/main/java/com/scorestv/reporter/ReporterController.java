package com.scorestv.reporter;

import com.scorestv.common.ApiException;
import com.scorestv.reporter.ReporterDtos.ActionRequest;
import com.scorestv.reporter.ReporterDtos.ApplicationView;
import com.scorestv.reporter.ReporterDtos.ApplyRequest;
import com.scorestv.reporter.ReporterDtos.CreateFixtureRequest;
import com.scorestv.reporter.ReporterDtos.CreateTeamRequest;
import com.scorestv.reporter.ReporterDtos.FixtureView;
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

    public ReporterController(ReporterService service) {
        this.service = service;
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

    /** Canlı konsol aksiyonu (START/GOAL_HOME/.../FINISH). */
    @PostMapping("/fixtures/{fixtureId}/actions")
    public FixtureView action(@PathVariable Long fixtureId,
                              @Valid @RequestBody ActionRequest req,
                              @AuthenticationPrincipal CurrentUser user) {
        return service.action(uid(user), fixtureId, req);
    }
}

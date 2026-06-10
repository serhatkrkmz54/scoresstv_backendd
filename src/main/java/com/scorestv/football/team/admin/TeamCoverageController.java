package com.scorestv.football.team.admin;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.scorestv.football.sync.CoachesSyncService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN'in takimlarin {@code covered} bayragini yonetmesi icin endpoint'ler.
 *
 * <p>{@code covered=true} olan takimlar periyodik joblar (DailyTeamRefreshJob)
 * tarafindan gunluk tazelenir — squad, transfers, coach, statistics, sidelined.
 * Bu sayede populer takim sayfalari her zaman taze veri ile acilir.
 *
 * <p>Tipik kullanim: 50-100 populer takimi (sampiyon ligi gercek katilanlari +
 * yerel top liglerin ust yarisi) covered isaretle.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/teams")
@PreAuthorize("hasRole('ADMIN')")
public class TeamCoverageController {

    private static final Logger log = LoggerFactory.getLogger(TeamCoverageController.class);

    private final TeamRepository teamRepository;
    private final CoachRepository coachRepository;
    private final CoachesSyncService coachesSyncService;

    public TeamCoverageController(TeamRepository teamRepository,
                                  CoachRepository coachRepository,
                                  CoachesSyncService coachesSyncService) {
        this.teamRepository = teamRepository;
        this.coachRepository = coachRepository;
        this.coachesSyncService = coachesSyncService;
    }

    /** Tek bir takimin covered bayragini set eder. */
    @PutMapping("/{id}/coverage")
    @Transactional
    public CoverageToggleResult setCoverage(@PathVariable Long id,
                                            @RequestParam boolean covered) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi: " + id));
        boolean previous = team.isCovered();
        team.setCovered(covered);
        teamRepository.save(team);
        log.info("Takim coverage degistirildi: teamId={} '{}' {} → {}",
                id, team.getName(), previous, covered);
        return new CoverageToggleResult(id, team.getName(), previous, covered);
    }

    /**
     * Birden cok takimi tek istekte covered=true isaretler. Toplu kurulum icin.
     *
     * <p>Ornek: {@code PUT /admin/.../teams/coverage/bulk?ids=549,541,529,40}
     */
    @PutMapping("/coverage/bulk")
    @Transactional
    public BulkCoverageResult setCoverageBulk(@RequestParam List<Long> ids,
                                              @RequestParam(defaultValue = "true") boolean covered) {
        int updated = 0;
        int notFound = 0;
        for (Long id : ids) {
            Team team = teamRepository.findById(id).orElse(null);
            if (team == null) {
                notFound++;
                continue;
            }
            if (team.isCovered() != covered) {
                team.setCovered(covered);
                teamRepository.save(team);
            }
            updated++;
        }
        log.info("Toplu takim coverage degisikligi: {} takim {} → {} (eksik: {})",
                updated, !covered, covered, notFound);
        return new BulkCoverageResult(updated, notFound, covered);
    }

    /** Su an covered olan tum takimlari listele. */
    @GetMapping("/coverage")
    public List<TeamCoverageRow> listCovered() {
        return teamRepository.findByCoveredTrue().stream()
                .map(t -> new TeamCoverageRow(
                        t.getId(), t.getName(), t.getCountry()))
                .toList();
    }

    public record CoverageToggleResult(
            Long teamId, String teamName,
            boolean previous, boolean current) {}

    public record BulkCoverageResult(
            int updated, int notFound, boolean covered) {}

    public record TeamCoverageRow(
            Long id, String name, String country) {}

    /**
     * Bir takima manuel bas antrenor override'i set eder. Picker bundan
     * sonra lineup/rule kontrolune dusmez — direkt bu coach'u kullanir.
     *
     * <p>Sirayla:
     * <ol>
     *   <li>Coach DB'de var mi kontrol et (yoksa 404)</li>
     *   <li>{@code team.head_coach_override_id} set et</li>
     *   <li>Hemen coach sync tetikle → cache cevabi guncellenmis halde dondur</li>
     * </ol>
     */
    @PutMapping("/{teamId}/head-coach")
    @Transactional
    public HeadCoachOverrideResult setHeadCoachOverride(
            @PathVariable Long teamId,
            @RequestParam Long coachId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi: " + teamId));
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> ApiException.notFound("Coach bulunamadi: " + coachId));
        Long previous = team.getHeadCoachOverrideId();
        team.setHeadCoachOverrideId(coachId);
        teamRepository.save(team);
        log.info("Head coach override: teamId={} '{}' previousOverride={} → coachId={} ({})",
                teamId, team.getName(), previous, coachId, coach.getName());
        // Override degisti — coach sync hemen tetikle, current_team_id dogru atansin.
        try {
            coachesSyncService.syncByTeam(teamId);
        } catch (RuntimeException ex) {
            log.warn("Override sonrasi coach sync hatasi: {}", ex.getMessage());
        }
        return new HeadCoachOverrideResult(teamId, team.getName(),
                previous, coachId, coach.getName());
    }

    /**
     * Manuel override'i kaldirir → otomatik picker (lineup → rule) devreye girer.
     */
    @DeleteMapping("/{teamId}/head-coach")
    @Transactional
    public HeadCoachOverrideResult clearHeadCoachOverride(@PathVariable Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi: " + teamId));
        Long previous = team.getHeadCoachOverrideId();
        team.setHeadCoachOverrideId(null);
        teamRepository.save(team);
        log.info("Head coach override KALDIRILDI: teamId={} '{}' previousOverride={}",
                teamId, team.getName(), previous);
        try {
            coachesSyncService.syncByTeam(teamId);
        } catch (RuntimeException ex) {
            log.warn("Override kaldirma sonrasi coach sync hatasi: {}", ex.getMessage());
        }
        return new HeadCoachOverrideResult(teamId, team.getName(),
                previous, null, null);
    }

    public record HeadCoachOverrideResult(
            Long teamId, String teamName,
            Long previousOverrideCoachId,
            Long currentOverrideCoachId,
            String currentOverrideCoachName) {}
}

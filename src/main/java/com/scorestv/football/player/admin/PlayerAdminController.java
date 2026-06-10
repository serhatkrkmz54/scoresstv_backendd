package com.scorestv.football.player.admin;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.sync.PlayerCareerTeamsSyncService;
import com.scorestv.football.sync.PlayerProfileSyncService;
import com.scorestv.football.sync.PlayerTrophiesSyncService;
import com.scorestv.football.sync.SidelinedSyncService;
import com.scorestv.football.sync.TransfersSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN'in oyuncu sayfasi alt modullerini elle senkronlamak + coverage
 * yonetmek icin kullandigi endpoint koleksiyonu.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/players")
@PreAuthorize("hasRole('ADMIN')")
public class PlayerAdminController {

    private static final Logger log = LoggerFactory.getLogger(PlayerAdminController.class);

    private final PlayerRepository playerRepository;
    private final PlayerProfileSyncService profileSyncService;
    private final PlayerCareerTeamsSyncService careerTeamsSyncService;
    private final PlayerTrophiesSyncService trophiesSyncService;
    private final TransfersSyncService transfersSyncService;
    private final SidelinedSyncService sidelinedSyncService;

    public PlayerAdminController(PlayerRepository playerRepository,
                                 PlayerProfileSyncService profileSyncService,
                                 PlayerCareerTeamsSyncService careerTeamsSyncService,
                                 PlayerTrophiesSyncService trophiesSyncService,
                                 TransfersSyncService transfersSyncService,
                                 SidelinedSyncService sidelinedSyncService) {
        this.playerRepository = playerRepository;
        this.profileSyncService = profileSyncService;
        this.careerTeamsSyncService = careerTeamsSyncService;
        this.trophiesSyncService = trophiesSyncService;
        this.transfersSyncService = transfersSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
    }

    // ============================================================
    // Coverage
    // ============================================================

    @PutMapping("/{id}/coverage")
    @Transactional
    public CoverageToggleResult setCoverage(@PathVariable Long id,
                                            @RequestParam boolean covered) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Oyuncu bulunamadi: " + id));
        boolean previous = player.isCovered();
        player.setCovered(covered);
        playerRepository.save(player);
        log.info("Player coverage degistirildi: playerId={} '{}' {} → {}",
                id, player.getName(), previous, covered);
        return new CoverageToggleResult(id, player.getName(), previous, covered);
    }

    @PutMapping("/coverage/bulk")
    @Transactional
    public BulkCoverageResult setCoverageBulk(@RequestParam List<Long> ids,
                                              @RequestParam(defaultValue = "true") boolean covered) {
        int updated = 0;
        int notFound = 0;
        for (Long id : ids) {
            Player player = playerRepository.findById(id).orElse(null);
            if (player == null) {
                notFound++;
                continue;
            }
            if (player.isCovered() != covered) {
                player.setCovered(covered);
                playerRepository.save(player);
            }
            updated++;
        }
        return new BulkCoverageResult(updated, notFound, covered);
    }

    @GetMapping("/coverage")
    public List<PlayerCoverageRow> listCovered() {
        return playerRepository.findByCoveredTrue().stream()
                .map(p -> new PlayerCoverageRow(
                        p.getId(), p.getName(), p.getNationality()))
                .toList();
    }

    // ============================================================
    // Manual sync endpoint'leri (debug/test)
    // ============================================================

    /** Tek oyuncunun belirli sezonu icin profile + stats. */
    @PostMapping("/{playerId}/profile/sync")
    public int syncProfile(@PathVariable Long playerId,
                            @RequestParam Integer season) {
        return profileSyncService.sync(playerId, season);
    }

    /** Kariyer takimlari — /players/teams?player=X */
    @PostMapping("/{playerId}/career-teams/sync")
    public int syncCareerTeams(@PathVariable Long playerId) {
        return careerTeamsSyncService.sync(playerId);
    }

    /** Kupalar — /trophies?player=X */
    @PostMapping("/{playerId}/trophies/sync")
    public int syncTrophies(@PathVariable Long playerId) {
        return trophiesSyncService.sync(playerId);
    }

    /** Transfer geçmişi — /transfers?player=X */
    @PostMapping("/{playerId}/transfers/sync")
    public int syncTransfers(@PathVariable Long playerId) {
        return transfersSyncService.syncByPlayer(playerId);
    }

    /** Sakatlık geçmişi — /sidelined?player=X */
    @PostMapping("/{playerId}/sidelined/sync")
    public int syncSidelined(@PathVariable Long playerId) {
        return sidelinedSyncService.syncOne(playerId);
    }

    public record CoverageToggleResult(
            Long playerId, String playerName,
            boolean previous, boolean current) {}

    public record BulkCoverageResult(
            int updated, int notFound, boolean covered) {}

    public record PlayerCoverageRow(
            Long id, String name, String nationality) {}
}

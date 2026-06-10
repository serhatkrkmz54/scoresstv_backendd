package com.scorestv.football.sync;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN'in takım detay sayfası alt modüllerini elle senkronlamak için
 * kullandığı admin endpoint koleksiyonu.
 *
 * <p>Tek bir takım ID veya yardımcı parametrelerle (ligId, season, playerIds)
 * her senkronu ayrı tetiklemeye olanak verir — debug/test ve coverage
 * doldurma için.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/teams/{teamId}")
@PreAuthorize("hasRole('ADMIN')")
public class TeamDetailSyncController {

    private final TeamSyncService teamSyncService;
    private final TeamStatisticsSyncService statsSyncService;
    private final SquadSyncService squadSyncService;
    private final TransfersSyncService transfersSyncService;
    private final CoachesSyncService coachesSyncService;
    private final CoachTrophiesSyncService coachTrophiesSyncService;
    private final SidelinedSyncService sidelinedSyncService;
    private final PlayerSeasonStatsSyncService playerStatsSyncService;

    public TeamDetailSyncController(TeamSyncService teamSyncService,
                                    TeamStatisticsSyncService statsSyncService,
                                    SquadSyncService squadSyncService,
                                    TransfersSyncService transfersSyncService,
                                    CoachesSyncService coachesSyncService,
                                    CoachTrophiesSyncService coachTrophiesSyncService,
                                    SidelinedSyncService sidelinedSyncService,
                                    PlayerSeasonStatsSyncService playerStatsSyncService) {
        this.teamSyncService = teamSyncService;
        this.statsSyncService = statsSyncService;
        this.squadSyncService = squadSyncService;
        this.transfersSyncService = transfersSyncService;
        this.coachesSyncService = coachesSyncService;
        this.coachTrophiesSyncService = coachTrophiesSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
    }

    /** Tek takım — /teams?id=X */
    @PostMapping("/sync")
    public int syncTeam(@PathVariable Long teamId) {
        return teamSyncService.syncOne(teamId);
    }

    /** Takım istatistikleri — /teams/statistics?team=X&league=Y&season=Z */
    @PostMapping("/statistics/sync")
    public int syncStatistics(@PathVariable Long teamId,
                              @RequestParam Long leagueId,
                              @RequestParam Integer season) {
        return statsSyncService.sync(teamId, leagueId, season);
    }

    /** Kadro — /players/squads?team=X */
    @PostMapping("/squad/sync")
    public int syncSquad(@PathVariable Long teamId,
                         @RequestParam(required = false) Integer season) {
        return squadSyncService.sync(teamId, season);
    }

    /**
     * Transferler — /transfers?team=X. SENKRON: response donmeden once tum
     * 263 oyuncu × ~3 transfer (~800 satir) DB'ye yazilir. 3-10sn surebilir.
     * Cache evict bilesik degildir; yeni sync sonrasi public team detail
     * 15sn icinde tazelenir (LIVE cache TTL).
     */
    @PostMapping("/transfers/sync")
    public int syncTransfers(@PathVariable Long teamId) {
        return transfersSyncService.syncByTeam(teamId);
    }

    /** Mevcut kocun bilgisi + kariyeri — /coachs?team=X (coach ID döner). */
    @PostMapping("/coach/sync")
    public Long syncCoach(@PathVariable Long teamId) {
        return coachesSyncService.syncByTeam(teamId);
    }

    /** Bir kocun kupaları — /trophies?coach=X */
    @PostMapping("/coach/{coachId}/trophies/sync")
    public int syncCoachTrophies(@PathVariable Long teamId,
                                 @PathVariable Long coachId) {
        return coachTrophiesSyncService.sync(coachId);
    }

    /** Kadronun aktif sakatları — /sidelined?player=X (her oyuncu icin). */
    @PostMapping("/sidelined/sync")
    public int syncSidelined(@PathVariable Long teamId,
                             @RequestParam List<Long> playerIds) {
        return sidelinedSyncService.syncForPlayers(playerIds);
    }

    /** Oyuncu sezonluk istatistikleri — /players?team=X&season=Y (sayfali). */
    @PostMapping("/playerstats/sync")
    public int syncPlayerStats(@PathVariable Long teamId,
                               @RequestParam Integer season) {
        return playerStatsSyncService.sync(teamId, season);
    }
}

package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TeamSquad;
import com.scorestv.football.domain.TeamSquadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Covered (kapsamli) takimlarin alt modullerini gunluk tazeleyen iş:
 * <ul>
 *   <li>Takim info (/teams?id) — temel veri, founded, ulke, venue</li>
 *   <li>Squad (/players/squads?team) — guncel kadro</li>
 *   <li>Transfers (/transfers?team) — son hareketler</li>
 *   <li>Sidelined (/sidelined?player) — kadronun sakat oyuncu listesi</li>
 * </ul>
 *
 * <p>Coach modulu kullanici karari ile kapatildi — API yanlis veri donuyordu.
 *
 * <p>Cron varsayilan {@code 0 30 6 * * *} (06:30 — DailyLeagueRefreshJob'dan
 * sonra). Bean yalniz {@code scorestv.football.sync.enabled=true} ile aktif.
 *
 * <p>Quota: ~100 covered takim × ~4 cagri/takim ~= 400 cagri/gun. Sidelined
 * per-player oldugundan kadro buyukluguyle (~25) carpilirsa toplam ~2500
 * cagri olur. Ultra plan 75k icinde rahatlikla yer alir.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyTeamRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(DailyTeamRefreshJob.class);

    private final TeamRepository teamRepository;
    private final TeamSquadRepository squadRepository;
    private final FixtureRepository fixtureRepository;

    private final TeamSyncService teamSyncService;
    private final SquadSyncService squadSyncService;
    private final TransfersSyncService transfersSyncService;
    private final SidelinedSyncService sidelinedSyncService;
    private final PlayerSeasonStatsSyncService playerStatsSyncService;
    private final CoachesSyncService coachesSyncService;
    private final CoachTrophiesSyncService coachTrophiesSyncService;

    public DailyTeamRefreshJob(TeamRepository teamRepository,
                               TeamSquadRepository squadRepository,
                               FixtureRepository fixtureRepository,
                               TeamSyncService teamSyncService,
                               SquadSyncService squadSyncService,
                               TransfersSyncService transfersSyncService,
                               SidelinedSyncService sidelinedSyncService,
                               PlayerSeasonStatsSyncService playerStatsSyncService,
                               CoachesSyncService coachesSyncService,
                               CoachTrophiesSyncService coachTrophiesSyncService) {
        this.teamRepository = teamRepository;
        this.squadRepository = squadRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamSyncService = teamSyncService;
        this.squadSyncService = squadSyncService;
        this.transfersSyncService = transfersSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.coachesSyncService = coachesSyncService;
        this.coachTrophiesSyncService = coachTrophiesSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.team-refresh-cron:0 30 6 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "dailyTeamRefresh", lockAtMostFor = "PT30M")
    public void run() {
        List<Team> covered = teamRepository.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.info("DailyTeamRefreshJob: covered takim yok, atlandı.");
            return;
        }
        int processed = 0;
        int infoFail = 0;
        int squadFail = 0;
        int transferFail = 0;
        int sidelinedFail = 0;

        for (Team team : covered) {
            Long teamId = team.getId();
            // 1) Team info
            try {
                teamSyncService.syncOne(teamId);
            } catch (RuntimeException ex) {
                infoFail++;
                log.warn("Takim info refresh basarisiz: teamId={} — {}", teamId, ex.getMessage());
            }
            // 2) Squad — current season (takimin DB'deki en son sezon yili)
            Integer currentSeason = resolveCurrentSeason(teamId);
            if (currentSeason != null) {
                try {
                    squadSyncService.sync(teamId, currentSeason);
                } catch (RuntimeException ex) {
                    squadFail++;
                    log.warn("Squad refresh basarisiz: teamId={} — {}",
                            teamId, ex.getMessage());
                }
            }
            // 3) Transfers
            try {
                transfersSyncService.syncByTeam(teamId);
            } catch (RuntimeException ex) {
                transferFail++;
                log.warn("Transfers refresh basarisiz: teamId={} — {}",
                        teamId, ex.getMessage());
            }
            // 3.5) Coach (+ trophies) — sezondan bagimsiz
            Long coachId = null;
            try {
                coachId = coachesSyncService.syncByTeam(teamId);
            } catch (RuntimeException ex) {
                log.warn("Coach refresh basarisiz: teamId={} — {}",
                        teamId, ex.getMessage());
            }
            if (coachId != null) {
                try {
                    coachTrophiesSyncService.sync(coachId);
                } catch (RuntimeException ex) {
                    log.warn("Coach trophies refresh basarisiz: coachId={} — {}",
                            coachId, ex.getMessage());
                }
            }
            // 4) Player season stats — sayfali, takim+sezon
            if (currentSeason != null) {
                try {
                    playerStatsSyncService.sync(teamId, currentSeason);
                } catch (RuntimeException ex) {
                    log.warn("Player stats refresh basarisiz: teamId={} — {}",
                            teamId, ex.getMessage());
                }
            }
            // 5) Sidelined — kadrodaki tum oyuncular icin
            if (currentSeason != null) {
                try {
                    Set<Long> playerIds = new HashSet<>();
                    for (TeamSquad s : squadRepository.findByTeamIdAndSeason(teamId, currentSeason)) {
                        if (s.getPlayerId() != null) playerIds.add(s.getPlayerId());
                    }
                    if (!playerIds.isEmpty()) {
                        sidelinedSyncService.syncForPlayers(playerIds);
                    }
                } catch (RuntimeException ex) {
                    sidelinedFail++;
                    log.warn("Sidelined refresh basarisiz: teamId={} — {}",
                            teamId, ex.getMessage());
                }
            }
            processed++;
        }
        log.info("DailyTeamRefreshJob bitti: {} takim islendi — info {} hata, "
                        + "squad {} hata, transfer {} hata, sidelined {} hata.",
                processed, infoFail, squadFail, transferFail, sidelinedFail);
    }

    /** Takimin DB'deki en son sezonu — fikstur tablosundan. */
    private Integer resolveCurrentSeason(Long teamId) {
        try {
            List<Integer> years = fixtureRepository.findSeasonYearsByTeam(teamId);
            return years.isEmpty() ? null : years.get(0);
        } catch (ApiException ex) {
            return null;
        }
    }
}

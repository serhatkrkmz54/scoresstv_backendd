package com.scorestv.football.sync;

import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Covered oyuncularin profil/kariyer takimlari/kupalar/transferler/sakatlik/
 * current sezon istatistiklerini gunluk tazeleyen iş.
 *
 * <p>Cron varsayilan {@code 0 0 7 * * *} (07:00 — DailyTeamRefreshJob'dan sonra).
 *
 * <p>Quota: ~100 covered oyuncu × 5 cagri/gun ~= 500 cagri/gun (profile +
 * career-teams + trophies + transfers + sidelined). Mevcut sezon stat'i da
 * profile sync icinde ayni cagriya dahil — extra yok.
 */
@Component
@ConditionalOnProperty(name = "scorestv.football.sync.enabled", havingValue = "true")
public class DailyPlayerRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(DailyPlayerRefreshJob.class);

    private final PlayerRepository playerRepository;
    private final PlayerSeasonStatRepository statRepository;
    private final PlayerProfileSyncService profileSyncService;
    private final PlayerCareerTeamsSyncService careerTeamsSyncService;
    private final PlayerTrophiesSyncService trophiesSyncService;
    private final TransfersSyncService transfersSyncService;
    private final SidelinedSyncService sidelinedSyncService;

    public DailyPlayerRefreshJob(PlayerRepository playerRepository,
                                 PlayerSeasonStatRepository statRepository,
                                 PlayerProfileSyncService profileSyncService,
                                 PlayerCareerTeamsSyncService careerTeamsSyncService,
                                 PlayerTrophiesSyncService trophiesSyncService,
                                 TransfersSyncService transfersSyncService,
                                 SidelinedSyncService sidelinedSyncService) {
        this.playerRepository = playerRepository;
        this.statRepository = statRepository;
        this.profileSyncService = profileSyncService;
        this.careerTeamsSyncService = careerTeamsSyncService;
        this.trophiesSyncService = trophiesSyncService;
        this.transfersSyncService = transfersSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
    }

    @Scheduled(
            cron = "${scorestv.football.sync.player-refresh-cron:0 0 7 * * *}",
            zone = "${scorestv.football.sync.timezone:Europe/Istanbul}")
    @SchedulerLock(name = "dailyPlayerRefresh", lockAtMostFor = "PT30M")
    public void run() {
        List<Player> covered = playerRepository.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.info("DailyPlayerRefreshJob: covered oyuncu yok, atlandı.");
            return;
        }
        int processed = 0;
        int errors = 0;
        for (Player player : covered) {
            Long playerId = player.getId();
            // Current season = DB'deki en yeni sezon
            Integer currentSeason = resolveCurrentSeason(playerId);
            // 1) Profile + current sezon stats (tek API call)
            if (currentSeason != null) {
                runQuietly("profile", playerId,
                        () -> profileSyncService.sync(playerId, currentSeason));
            }
            // 2) Career teams
            runQuietly("career-teams", playerId,
                    () -> careerTeamsSyncService.sync(playerId));
            // 3) Trophies
            runQuietly("trophies", playerId,
                    () -> trophiesSyncService.sync(playerId));
            // 4) Transfers
            runQuietly("transfers", playerId,
                    () -> transfersSyncService.syncByPlayer(playerId));
            // 5) Sidelined
            runQuietly("sidelined", playerId,
                    () -> sidelinedSyncService.syncOne(playerId));
            processed++;
        }
        log.info("DailyPlayerRefreshJob bitti: {} oyuncu islendi, {} hata.",
                processed, errors);
    }

    private Integer resolveCurrentSeason(Long playerId) {
        List<Integer> years = statRepository.findSeasonYearsByPlayer(playerId);
        return years.isEmpty() ? null : years.get(0);
    }

    private void runQuietly(String module, Long playerId, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            log.warn("DailyPlayerRefresh {} sync hatasi: playerId={} — {}",
                    module, playerId, ex.getMessage());
        }
    }
}

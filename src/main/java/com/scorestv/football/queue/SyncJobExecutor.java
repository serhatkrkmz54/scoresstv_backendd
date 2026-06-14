package com.scorestv.football.queue;

import com.scorestv.basketball.BasketballLeaguesSyncService;
import com.scorestv.basketball.BasketballPlayerProfileSyncService;
import com.scorestv.basketball.BasketballTeamProfileSyncService;
import com.scorestv.basketball.BasketballTeamStatisticsSyncService;
import com.scorestv.basketball.BasketballTopPlayersSyncService;
import com.scorestv.football.sync.CoachesSyncService;
import com.scorestv.football.sync.PlayerCareerTeamsSyncService;
import com.scorestv.football.sync.PlayerProfileSyncService;
import com.scorestv.football.sync.PlayerSeasonStatsSyncService;
import com.scorestv.football.sync.PlayerTrophiesSyncService;
import com.scorestv.football.sync.SidelinedSyncService;
import com.scorestv.football.sync.SquadSyncService;
import com.scorestv.football.sync.StandingsSyncService;
import com.scorestv.football.sync.TeamSyncService;
import com.scorestv.football.sync.TopPlayersSyncService;
import com.scorestv.football.sync.TransfersSyncService;
import com.scorestv.football.domain.LeagueTopPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Job dispatcher — {@link SyncJob}'in {@link SyncJobType}'una gore ilgili
 * sync servisini cagirir. Worker bunu cagirir.
 *
 * <p>Payload extraction: Long/Integer cast'lerine dikkat — JSONB deserializer
 * sayilari Integer olarak dondurebilir, biz Long bekledigimiz yerde donduruyoruz.
 */
@Service
public class SyncJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncJobExecutor.class);

    // Team sync servisleri
    private final SquadSyncService squadSyncService;
    private final TransfersSyncService transfersSyncService;
    private final CoachesSyncService coachesSyncService;
    private final PlayerSeasonStatsSyncService playerSeasonStatsSyncService;

    // Player sync servisleri
    private final PlayerProfileSyncService playerProfileSyncService;
    private final PlayerCareerTeamsSyncService playerCareerTeamsSyncService;
    private final PlayerTrophiesSyncService playerTrophiesSyncService;
    private final SidelinedSyncService sidelinedSyncService;

    // League sync servisleri
    private final StandingsSyncService standingsSyncService;
    private final TopPlayersSyncService topPlayersSyncService;
    private final TeamSyncService teamSyncService;

    // Bulk league players dump — yeniden enqueue icin
    private final SyncQueueService queueService;

    // Basketbol sync servisleri
    private final BasketballLeaguesSyncService basketballLeaguesSyncService;
    private final BasketballTopPlayersSyncService basketballTopPlayersSyncService;
    private final BasketballPlayerProfileSyncService basketballPlayerProfileSyncService;
    private final BasketballTeamProfileSyncService basketballTeamProfileSyncService;
    private final BasketballTeamStatisticsSyncService basketballTeamStatisticsSyncService;

    public SyncJobExecutor(SquadSyncService squadSyncService,
                           TransfersSyncService transfersSyncService,
                           CoachesSyncService coachesSyncService,
                           PlayerSeasonStatsSyncService playerSeasonStatsSyncService,
                           PlayerProfileSyncService playerProfileSyncService,
                           PlayerCareerTeamsSyncService playerCareerTeamsSyncService,
                           PlayerTrophiesSyncService playerTrophiesSyncService,
                           SidelinedSyncService sidelinedSyncService,
                           StandingsSyncService standingsSyncService,
                           TopPlayersSyncService topPlayersSyncService,
                           TeamSyncService teamSyncService,
                           SyncQueueService queueService,
                           BasketballLeaguesSyncService basketballLeaguesSyncService,
                           BasketballTopPlayersSyncService basketballTopPlayersSyncService,
                           BasketballPlayerProfileSyncService basketballPlayerProfileSyncService,
                           BasketballTeamProfileSyncService basketballTeamProfileSyncService,
                           BasketballTeamStatisticsSyncService basketballTeamStatisticsSyncService) {
        this.squadSyncService = squadSyncService;
        this.transfersSyncService = transfersSyncService;
        this.coachesSyncService = coachesSyncService;
        this.playerSeasonStatsSyncService = playerSeasonStatsSyncService;
        this.playerProfileSyncService = playerProfileSyncService;
        this.playerCareerTeamsSyncService = playerCareerTeamsSyncService;
        this.playerTrophiesSyncService = playerTrophiesSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
        this.standingsSyncService = standingsSyncService;
        this.topPlayersSyncService = topPlayersSyncService;
        this.teamSyncService = teamSyncService;
        this.queueService = queueService;
        this.basketballLeaguesSyncService = basketballLeaguesSyncService;
        this.basketballTopPlayersSyncService = basketballTopPlayersSyncService;
        this.basketballPlayerProfileSyncService = basketballPlayerProfileSyncService;
        this.basketballTeamProfileSyncService = basketballTeamProfileSyncService;
        this.basketballTeamStatisticsSyncService = basketballTeamStatisticsSyncService;
    }

    /**
     * Job'u execute eder. Hata firlatirsa worker yakalar — burada kotrol akisi
     * yalniz dispatch.
     *
     * @return basarili olmasi durumunda yazilan satir sayisi (job_type'a gore
     *         farkli anlama gelir — squad icin oyuncu sayisi, trophies icin
     *         kupa sayisi vs.)
     */
    public int execute(SyncJob job) {
        SyncJobType type = job.getJobType();
        Map<String, Object> p = job.getPayload();
        return switch (type) {
            case TEAM_SQUAD_SYNC -> squadSyncService.sync(
                    asLong(p, "teamId"), asInt(p, "season"));
            case TEAM_TRANSFERS_SYNC -> transfersSyncService.syncByTeam(
                    asLong(p, "teamId"));
            case TEAM_COACH_SYNC -> {
                Long coachId = coachesSyncService.syncByTeam(asLong(p, "teamId"));
                yield coachId != null ? 1 : 0;
            }
            case TEAM_PLAYER_STATS_SYNC -> playerSeasonStatsSyncService.sync(
                    asLong(p, "teamId"), asInt(p, "season"));

            case PLAYER_PROFILE_SYNC -> playerProfileSyncService.sync(
                    asLong(p, "playerId"), asInt(p, "season"));
            case PLAYER_CAREER_TEAMS_SYNC -> playerCareerTeamsSyncService.sync(
                    asLong(p, "playerId"));
            case PLAYER_TROPHIES_SYNC -> playerTrophiesSyncService.sync(
                    asLong(p, "playerId"));
            case PLAYER_TRANSFERS_SYNC -> transfersSyncService.syncByPlayer(
                    asLong(p, "playerId"));
            case PLAYER_SIDELINED_SYNC -> sidelinedSyncService.syncOne(
                    asLong(p, "playerId"));

            case LEAGUE_STANDINGS_SYNC -> standingsSyncService.sync(
                    asLong(p, "leagueId"), asInt(p, "season")).rowsWritten();
            case LEAGUE_TOP_PLAYERS_SYNC -> topPlayersSyncService.sync(
                    asLong(p, "leagueId"), asInt(p, "season"),
                    LeagueTopPlayer.Category.valueOf((String) p.get("category"))).written();
            case LEAGUE_TEAMS_SYNC -> teamSyncService.syncLeague(
                    asLong(p, "leagueId"), asInt(p, "season"));

            case LEAGUE_PLAYERS_DUMP -> executeLeaguePlayersDump(p);

            // ---- Basketbol ----
            case BASKETBALL_LEAGUE_INFO_SYNC -> {
                var saved = basketballLeaguesSyncService.syncLeagueInfo(
                        asLong(p, "leagueId"));
                yield saved != null ? 1 : 0;
            }
            case BASKETBALL_LEAGUE_TOP_PLAYERS_SYNC ->
                basketballTopPlayersSyncService.syncLeagueSeason(
                        asLong(p, "leagueId"), asString(p, "season"));
            case BASKETBALL_PLAYER_PROFILE_SYNC -> {
                var saved = basketballPlayerProfileSyncService.syncProfile(
                        asLong(p, "playerId"),
                        asLong(p, "leagueId"),
                        asString(p, "season"));
                yield saved != null ? 1 : 0;
            }
            case BASKETBALL_TEAM_PROFILE_SYNC -> {
                var saved = basketballTeamProfileSyncService.syncProfile(
                        asLong(p, "teamId"), true);
                yield saved.isPresent() ? 1 : 0;
            }
            case BASKETBALL_TEAM_STATS_SYNC -> {
                var saved = basketballTeamStatisticsSyncService.sync(
                        asLong(p, "teamId"),
                        asLong(p, "leagueId"),
                        asString(p, "season"),
                        true);
                yield saved.isPresent() ? 1 : 0;
            }
        };
    }

    /**
     * Lig oyuncu dump'i — paginated. Bir job tek bir sayfa isler, sonraki
     * sayfa icin yeni job kuyruga eklenir. Yapay tek-thread paginasyon.
     */
    private int executeLeaguePlayersDump(Map<String, Object> p) {
        Long leagueId = asLong(p, "leagueId");
        Integer season = asInt(p, "season");
        Integer page = asInt(p, "page");
        if (page == null) page = 1;
        // Simdilik tek API call — PlayerSeasonStatsSyncService'in sayfali
        // versiyonu var, ama o team-bazli. Lig-bazli icin ayri sync gerekir.
        // Bu placeholder — admin endpoint'i bu job'lari uretirken pagination
        // yapsin daha temiz.
        log.info("LEAGUE_PLAYERS_DUMP placeholder: leagueId={} season={} page={}",
                leagueId, season, page);
        return 0;
    }

    private static Long asLong(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static Integer asInt(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    /** Basketbol sezonlari "2024-2025" formatinda string — tip-guvenli getter. */
    private static String asString(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? null : v.toString();
    }
}

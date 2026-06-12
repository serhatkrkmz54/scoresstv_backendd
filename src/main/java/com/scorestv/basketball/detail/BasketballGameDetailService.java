package com.scorestv.basketball.detail;

import com.scorestv.basketball.BasketballMessages;
import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGamePlayerStat;
import com.scorestv.basketball.domain.BasketballGamePlayerStatRepository;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballGameTeamStat;
import com.scorestv.basketball.domain.BasketballGameTeamStatRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballStanding;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.seo.BasketballGameDetailSeoBuilder;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.H2hGameView;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.LeagueRef;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.MadeAttempt;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.PlayerStatGroup;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.PlayerStatRow;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.Rebounds;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.ScoreBreakdown;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.SidescoreLine;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.StandingRow;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.StandingsGroup;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.Status;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.TeamRef;
import com.scorestv.basketball.web.dto.BasketballGameDetailResponse.TeamStatsView;
import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Basketbol mac detayi sayfasi servis katmani — futbol
 * {@code MatchDetailService}'nin esi.
 *
 * <p><b>Mimari:</b> Public {@link #getById} eksik yan modulleri lazy sync ile
 * tetikler, sonra {@code self.loadCached(...)} proxy uzerinden cached/readOnly
 * gorunumu okur. Self-injection {@code @Lazy} ile circular bagimlilik onlenir.
 *
 * <p><b>Cache:</b> 30 saniye Redis TTL — canli maclar WebSocket guncellemesi
 * de aldigi icin kabul edilebilir. Cache key dil bilincli ({@code tr}/{@code en}).
 *
 * <p><b>SEO:</b> Service simdilik SEO icin null doner; B-Faz5'te
 * {@code BasketballGameDetailSeoBuilder} eklenince burada doldurulacak.
 */
@Service
public class BasketballGameDetailService {

    private static final Logger log = LoggerFactory.getLogger(BasketballGameDetailService.class);

    private static final String CACHE_NAME = "BASKETBALL_GAME_DETAIL";

    /** Henuz baslamamis durumlar — bunlar icin stats vs. yuklenmez. */
    private static final Set<String> NOT_STARTED = Set.of("NS", "TBD", "POST", "CANC", "SUSP");
    /** In-play durumlar. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT");

    private final BasketballGameRepository gameRepo;
    private final BasketballGameTeamStatRepository teamStatRepo;
    private final BasketballGamePlayerStatRepository playerStatRepo;
    private final BasketballStandingRepository standingRepo;
    private final BasketballGameDetailLazySync lazySync;
    private final BasketballMessages messages;
    private final BasketballGameDetailSeoBuilder seoBuilder;

    /** Self-proxy — cached metoda gercek invocation icin (AOP advice bypass'ini onler). */
    private final BasketballGameDetailService self;

    public BasketballGameDetailService(BasketballGameRepository gameRepo,
                                        BasketballGameTeamStatRepository teamStatRepo,
                                        BasketballGamePlayerStatRepository playerStatRepo,
                                        BasketballStandingRepository standingRepo,
                                        BasketballGameDetailLazySync lazySync,
                                        BasketballMessages messages,
                                        BasketballGameDetailSeoBuilder seoBuilder,
                                        @Lazy BasketballGameDetailService self) {
        this.gameRepo = gameRepo;
        this.teamStatRepo = teamStatRepo;
        this.playerStatRepo = playerStatRepo;
        this.standingRepo = standingRepo;
        this.lazySync = lazySync;
        this.messages = messages;
        this.seoBuilder = seoBuilder;
        this.self = self;
    }

    // ================================================================
    // Public entry
    // ================================================================

    /**
     * Mac detayi getir — orkestratör. Lazy sync tetikler (fire-and-forget),
     * ardindan cached/readOnly gorunum doner.
     *
     * @throws ApiException 404 — mac bulunamazsa
     */
    public BasketballGameDetailResponse getById(Long id, boolean turkish) {
        return getById(id, turkish, false);
    }

    /**
     * Mac detayi getir — force refresh destegi ile.
     * {@code forceRefresh=true} ise Redis cache evict edilir; bir sonraki
     * cagride lazy sync tetiklenir.
     */
    public BasketballGameDetailResponse getById(Long id, boolean turkish, boolean forceRefresh) {
        if (id == null) throw new ApiException(404, "Mac bulunamadi");
        BasketballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> new ApiException(404, "Mac bulunamadi"));

        if (forceRefresh) {
            self.evictCache(id, turkish);
        }

        // Lazy sync async — bekleme yok. Mevcut DB veri doner.
        try {
            lazySync.ensureAll(game);
        } catch (Exception e) {
            log.warn("Basketbol lazy sync tetikleme hatasi id={}: {}",
                    id, e.toString());
        }

        return self.loadCached(id, turkish);
    }

    // ================================================================
    // Cache'lenen read — readOnly tx, sync sonrasi DB tam dolu
    // ================================================================

    @Cacheable(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#id, #turkish)"
    )
    @Transactional(readOnly = true)
    public BasketballGameDetailResponse loadCached(Long id, boolean turkish) {
        BasketballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> new ApiException(404, "Mac bulunamadi"));
        return build(game, turkish);
    }

    @CacheEvict(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#id, #turkish)"
    )
    public void evictCache(Long id, boolean turkish) {
        // Spring evict — body bos
    }

    // ================================================================
    // DTO build
    // ================================================================

    private BasketballGameDetailResponse build(BasketballGame game, boolean turkish) {
        TeamRef home = mapTeam(game.getHomeTeam(), turkish);
        TeamRef away = mapTeam(game.getAwayTeam(), turkish);

        String homeName = home != null ? home.displayName() : "home";
        String awayName = away != null ? away.displayName() : "away";
        String slug = SlugUtil.gameSlug(homeName, awayName, game.getId());

        Status status = mapStatus(game, turkish);
        ScoreBreakdown score = mapScore(game);
        LeagueRef league = mapLeague(game.getLeague(), game.getSeason(), turkish);

        // Stats — DB'de yoksa bos liste
        List<TeamStatsView> teamStats = mapTeamStats(game, turkish);
        List<PlayerStatGroup> playerStats = mapPlayerStats(game, turkish);

        // H2H — mevcut maci dislayarak son 10
        List<H2hGameView> h2h = mapH2h(game, turkish);

        // Standings — grup-aware
        List<StandingsGroup> standings = mapStandings(game, turkish);

        var seo = seoBuilder.build(game, turkish ? "tr" : "en");

        return new BasketballGameDetailResponse(
                game.getId(),
                slug,
                game.getStage(),
                game.getWeek(),
                game.getStartAt(),
                game.getLastSyncedAt(),
                status,
                home,
                away,
                score,
                league,
                teamStats,
                playerStats,
                h2h,
                standings,
                seo
        );
    }

    /**
     * Standalone SEO endpoint icin — sadece SEO paketi doner, full DTO build
     * yapmaz. Slug'tan ID cikarip cagrilir.
     */
    @Transactional(readOnly = true)
    public BasketballGameDetailResponse.SeoBundle getSeoById(Long id, boolean turkish) {
        if (id == null) throw new ApiException(404, "Mac bulunamadi");
        BasketballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> new ApiException(404, "Mac bulunamadi"));
        return seoBuilder.build(game, turkish ? "tr" : "en");
    }

    private Status mapStatus(BasketballGame game, boolean turkish) {
        String statusText = messages.statusText(
                game.getStatusShort(), game.getStatusLong(), turkish);
        return new Status(
                game.getStatusShort(),
                game.getStatusLong(),
                game.getTimer(),
                statusText
        );
    }

    private TeamRef mapTeam(BasketballTeam team, boolean turkish) {
        if (team == null) return null;
        String displayName = turkish && team.getNameTr() != null && !team.getNameTr().isBlank()
                ? team.getNameTr()
                : team.getName();
        String slug = SlugUtil.teamSlug(displayName, team.getId());
        return new TeamRef(team.getId(), team.getName(), displayName, team.getLogo(), slug);
    }

    private ScoreBreakdown mapScore(BasketballGame g) {
        SidescoreLine h = new SidescoreLine(
                g.getHomeQ1(), g.getHomeQ2(), g.getHomeQ3(), g.getHomeQ4(),
                g.getHomeOt(), g.getHomeTotal());
        SidescoreLine a = new SidescoreLine(
                g.getAwayQ1(), g.getAwayQ2(), g.getAwayQ3(), g.getAwayQ4(),
                g.getAwayOt(), g.getAwayTotal());
        return new ScoreBreakdown(h, a);
    }

    private LeagueRef mapLeague(BasketballLeague league, String season, boolean turkish) {
        if (league == null) return null;
        String name = turkish && league.getNameTr() != null && !league.getNameTr().isBlank()
                ? league.getNameTr()
                : league.getName();
        String slug = SlugUtil.leagueSlug(name, league.getId());
        return new LeagueRef(
                league.getId(),
                league.getName(),
                league.getType(),
                slug,
                league.getLogo(),
                league.getCountryName(),
                league.getCountryFlag(),
                season
        );
    }

    private List<TeamStatsView> mapTeamStats(BasketballGame game, boolean turkish) {
        List<BasketballGameTeamStat> stats = teamStatRepo.findByGameId(game.getId());
        if (stats.isEmpty()) return List.of();
        List<TeamStatsView> out = new ArrayList<>(stats.size());
        for (BasketballGameTeamStat s : stats) {
            out.add(new TeamStatsView(
                    mapTeam(s.getTeam(), turkish),
                    new MadeAttempt(s.getFgTotal(), s.getFgAttempts(), s.getFgPercentage()),
                    new MadeAttempt(s.getTpTotal(), s.getTpAttempts(), s.getTpPercentage()),
                    new MadeAttempt(s.getFtTotal(), s.getFtAttempts(), s.getFtPercentage()),
                    new Rebounds(s.getReboundsTotal(), s.getReboundsOffence(), s.getReboundsDefense()),
                    s.getAssists(),
                    s.getSteals(),
                    s.getBlocks(),
                    s.getTurnovers(),
                    s.getPersonalFouls()
            ));
        }
        return out;
    }

    private List<PlayerStatGroup> mapPlayerStats(BasketballGame game, boolean turkish) {
        List<BasketballGamePlayerStat> stats = playerStatRepo.findByGameId(game.getId());
        if (stats.isEmpty()) return List.of();
        // team_id'ye gore grupla, sonra starters/bench ayir.
        Map<Long, BasketballTeam> teamRefs = new LinkedHashMap<>();
        Map<Long, List<PlayerStatRow>> starters = new LinkedHashMap<>();
        Map<Long, List<PlayerStatRow>> bench = new LinkedHashMap<>();
        for (BasketballGamePlayerStat s : stats) {
            Long tid = s.getTeam().getId();
            teamRefs.putIfAbsent(tid, s.getTeam());
            PlayerStatRow row = new PlayerStatRow(
                    s.getPlayer().getId(),
                    s.getPlayerName() != null ? s.getPlayerName() : s.getPlayer().getName(),
                    s.getMinutes(),
                    s.getPoints(),
                    new MadeAttempt(s.getFgTotal(), s.getFgAttempts(), s.getFgPercentage()),
                    new MadeAttempt(s.getTpTotal(), s.getTpAttempts(), s.getTpPercentage()),
                    new MadeAttempt(s.getFtTotal(), s.getFtAttempts(), s.getFtPercentage()),
                    new Rebounds(s.getReboundsTotal(), s.getReboundsOffence(), s.getReboundsDefense()),
                    s.getAssists(),
                    s.getSteals(),
                    s.getBlocks(),
                    s.getTurnovers(),
                    s.getPersonalFouls()
            );
            boolean isStarter = "starters".equalsIgnoreCase(s.getType());
            (isStarter ? starters : bench)
                    .computeIfAbsent(tid, k -> new ArrayList<>())
                    .add(row);
        }

        List<PlayerStatGroup> out = new ArrayList<>(teamRefs.size());
        for (Map.Entry<Long, BasketballTeam> e : teamRefs.entrySet()) {
            out.add(new PlayerStatGroup(
                    mapTeam(e.getValue(), turkish),
                    starters.getOrDefault(e.getKey(), List.of()),
                    bench.getOrDefault(e.getKey(), List.of())
            ));
        }
        return out;
    }

    private List<H2hGameView> mapH2h(BasketballGame game, boolean turkish) {
        if (game.getHomeTeam() == null || game.getAwayTeam() == null) return List.of();
        long t1 = game.getHomeTeam().getId();
        long t2 = game.getAwayTeam().getId();
        var page = PageRequest.of(0, 10);
        var rows = gameRepo.findH2h(t1, t2, game.getId(), page);
        if (rows.isEmpty()) return List.of();
        List<H2hGameView> out = new ArrayList<>(rows.size());
        for (BasketballGame g : rows) {
            TeamRef h = mapTeam(g.getHomeTeam(), turkish);
            TeamRef a = mapTeam(g.getAwayTeam(), turkish);
            String homeName = h != null ? h.displayName() : "home";
            String awayName = a != null ? a.displayName() : "away";
            String slug = SlugUtil.gameSlug(homeName, awayName, g.getId());
            String winnerSide = null;
            if (g.getHomeTotal() != null && g.getAwayTotal() != null) {
                if (g.getHomeTotal() > g.getAwayTotal()) winnerSide = "home";
                else if (g.getAwayTotal() > g.getHomeTotal()) winnerSide = "away";
                else winnerSide = "draw";   // basketbolda nadir ama defansif
            }
            out.add(new H2hGameView(
                    g.getId(), slug, g.getStartAt(),
                    g.getStatusShort(), g.getStatusShort(),
                    h, a,
                    g.getHomeTotal(), g.getAwayTotal(),
                    winnerSide
            ));
        }
        return out;
    }

    private List<StandingsGroup> mapStandings(BasketballGame game, boolean turkish) {
        if (game.getLeague() == null || game.getSeason() == null) return List.of();
        List<BasketballStanding> rows = standingRepo.findByLeagueAndSeason(
                game.getLeague().getId(), game.getSeason());
        if (rows.isEmpty()) return List.of();

        // Grup adina gore gruplandir, gruplar arasinda da gorunum sirasini koru
        // (groupName ascending). Tek-gruplu liglerde groupName = "".
        Map<String, List<StandingRow>> byGroup = new TreeMap<>();
        Map<String, String> stageByGroup = new TreeMap<>();
        for (BasketballStanding s : rows) {
            String gname = s.getGroupName() == null ? "" : s.getGroupName();
            byGroup.computeIfAbsent(gname, k -> new ArrayList<>())
                    .add(new StandingRow(
                            s.getPosition(),
                            mapTeam(s.getTeam(), turkish),
                            s.getGamesPlayedAll(),
                            s.getWonAll(),
                            s.getLostAll(),
                            s.getWonPercentage(),
                            s.getPointsFor(),
                            s.getPointsAgainst(),
                            (s.getPointsFor() != null && s.getPointsAgainst() != null)
                                    ? (s.getPointsFor() - s.getPointsAgainst()) : null,
                            s.getForm(),
                            s.getDescription(),
                            s.getDescription()   // B-Faz5 BasketballMessages'da cevrilecek
                    ));
            stageByGroup.putIfAbsent(gname, s.getStage());
        }
        List<StandingsGroup> out = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<StandingRow>> e : byGroup.entrySet()) {
            out.add(new StandingsGroup(e.getKey(), stageByGroup.get(e.getKey()), e.getValue()));
        }
        return out;
    }
}

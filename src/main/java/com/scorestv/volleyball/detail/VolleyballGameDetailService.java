package com.scorestv.volleyball.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.volleyball.VolleyballMessages;
import com.scorestv.volleyball.seo.VolleyballGameDetailSeoBuilder;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballStanding;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStat;
import com.scorestv.volleyball.domain.VolleyballTeamSeasonStatRepository;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.H2hGameView;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.LeagueRef;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.ScoreBreakdown;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.SetScore;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.StandingRow;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.StandingsGroup;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.Status;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.TeamRef;
import com.scorestv.volleyball.web.dto.VolleyballGameDetailResponse.TeamSeasonStatsView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Voleybol mac detayi sayfasi servis katmani — basketbol
 * {@code BasketballGameDetailService}'in voleybol esi, LEANER (oyuncu/mac-bazli
 * stats yok). Hero score (set + per-set) + standings + h2h + sezon team stats.
 */
@Service
public class VolleyballGameDetailService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballGameDetailService.class);

    private static final String CACHE_NAME = "VOLLEYBALL_GAME_DETAIL";

    private final VolleyballGameRepository gameRepo;
    private final VolleyballStandingRepository standingRepo;
    private final VolleyballTeamSeasonStatRepository teamStatRepo;
    private final VolleyballGameDetailLazySync lazySync;
    private final VolleyballMessages messages;
    private final VolleyballGameDetailSeoBuilder seoBuilder;

    private final VolleyballGameDetailService self;

    public VolleyballGameDetailService(VolleyballGameRepository gameRepo,
                                       VolleyballStandingRepository standingRepo,
                                       VolleyballTeamSeasonStatRepository teamStatRepo,
                                       VolleyballGameDetailLazySync lazySync,
                                       VolleyballMessages messages,
                                       VolleyballGameDetailSeoBuilder seoBuilder,
                                       @Lazy VolleyballGameDetailService self) {
        this.gameRepo = gameRepo;
        this.standingRepo = standingRepo;
        this.teamStatRepo = teamStatRepo;
        this.lazySync = lazySync;
        this.messages = messages;
        this.seoBuilder = seoBuilder;
        this.self = self;
    }

    public VolleyballGameDetailResponse getById(Long id, boolean turkish) {
        return getById(id, turkish, false);
    }

    public VolleyballGameDetailResponse getById(Long id, boolean turkish, boolean forceRefresh) {
        if (id == null) throw ApiException.notFound("Mac bulunamadi");
        VolleyballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Mac bulunamadi"));

        if (forceRefresh) {
            self.evictCache(id, turkish);
        }

        try {
            lazySync.ensureAll(game);
        } catch (Exception e) {
            log.warn("Voleybol lazy sync tetikleme hatasi id={}: {}", id, e.toString());
        }

        return self.loadCached(id, turkish);
    }

    @Cacheable(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#id, #turkish)"
    )
    @Transactional(readOnly = true)
    public VolleyballGameDetailResponse loadCached(Long id, boolean turkish) {
        VolleyballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Mac bulunamadi"));
        return build(game, turkish);
    }

    @CacheEvict(
            value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#id, #turkish)"
    )
    public void evictCache(Long id, boolean turkish) {
        // Spring evict — body bos
    }

    private VolleyballGameDetailResponse build(VolleyballGame game, boolean turkish) {
        TeamRef home = mapTeam(game.getHomeTeam(), turkish);
        TeamRef away = mapTeam(game.getAwayTeam(), turkish);

        String homeName = home != null ? home.displayName() : "home";
        String awayName = away != null ? away.displayName() : "away";
        String slug = SlugUtil.gameSlug(homeName, awayName, game.getId());

        Status status = mapStatus(game, turkish);
        ScoreBreakdown score = mapScore(game);
        LeagueRef league = mapLeague(game.getLeague(), game.getSeason(), turkish);

        List<TeamSeasonStatsView> teamStats = mapTeamStats(game, turkish);
        List<H2hGameView> h2h = mapH2h(game, turkish);
        List<StandingsGroup> standings = mapStandings(game, turkish);

        var seo = seoBuilder.build(game, turkish ? "tr" : "en");

        return new VolleyballGameDetailResponse(
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
    public VolleyballGameDetailResponse.SeoBundle getSeoById(Long id, boolean turkish) {
        if (id == null) throw ApiException.notFound("Mac bulunamadi");
        VolleyballGame game = gameRepo.findOneWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Mac bulunamadi"));
        return seoBuilder.build(game, turkish ? "tr" : "en");
    }

    private Status mapStatus(VolleyballGame game, boolean turkish) {
        String statusText = messages.statusText(
                game.getStatusShort(), game.getStatusLong(), turkish);
        return new Status(
                game.getStatusShort(),
                game.getStatusLong(),
                statusText
        );
    }

    private TeamRef mapTeam(VolleyballTeam team, boolean turkish) {
        if (team == null) return null;
        String displayName = turkish && team.getNameTr() != null && !team.getNameTr().isBlank()
                ? team.getNameTr()
                : team.getName();
        String slug = SlugUtil.teamSlug(displayName, team.getId());
        return new TeamRef(team.getId(), team.getName(), displayName, team.getLogo(), slug);
    }

    private ScoreBreakdown mapScore(VolleyballGame g) {
        List<SetScore> sets = new ArrayList<>(5);
        addSet(sets, 1, g.getHomeSet1(), g.getAwaySet1());
        addSet(sets, 2, g.getHomeSet2(), g.getAwaySet2());
        addSet(sets, 3, g.getHomeSet3(), g.getAwaySet3());
        addSet(sets, 4, g.getHomeSet4(), g.getAwaySet4());
        addSet(sets, 5, g.getHomeSet5(), g.getAwaySet5());
        return new ScoreBreakdown(g.getHomeTotal(), g.getAwayTotal(), sets);
    }

    /** Sadece oynanan setleri ekle (iki taraftan biri bos degilse). */
    private void addSet(List<SetScore> out, int n, Integer home, Integer away) {
        if (home == null && away == null) return;
        out.add(new SetScore(n, home, away));
    }

    private LeagueRef mapLeague(VolleyballLeague league, String season, boolean turkish) {
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

    private List<TeamSeasonStatsView> mapTeamStats(VolleyballGame game, boolean turkish) {
        if (game.getLeague() == null || game.getSeason() == null) return List.of();
        long leagueId = game.getLeague().getId();
        String season = game.getSeason();
        List<TeamSeasonStatsView> out = new ArrayList<>(2);
        for (VolleyballTeam team : new VolleyballTeam[]{game.getHomeTeam(), game.getAwayTeam()}) {
            if (team == null) continue;
            teamStatRepo.findByTeamIdAndLeagueIdAndSeason(team.getId(), leagueId, season)
                    .ifPresent(s -> out.add(new TeamSeasonStatsView(
                            mapTeam(team, turkish),
                            s.getGamesPlayed(),
                            s.getWins(),
                            s.getLoses(),
                            bdToString(s.getWinPercentage()),
                            s.getSetsForTotal(),
                            bdToDouble(s.getSetsForAvg()),
                            s.getSetsAgainstTotal(),
                            bdToDouble(s.getSetsAgainstAvg()),
                            s.getForm()
                    )));
        }
        return out;
    }

    private List<H2hGameView> mapH2h(VolleyballGame game, boolean turkish) {
        if (game.getHomeTeam() == null || game.getAwayTeam() == null) return List.of();
        long t1 = game.getHomeTeam().getId();
        long t2 = game.getAwayTeam().getId();
        var page = PageRequest.of(0, 10);
        var rows = gameRepo.findH2h(t1, t2, game.getId(), page);
        if (rows.isEmpty()) return List.of();
        List<H2hGameView> out = new ArrayList<>(rows.size());
        for (VolleyballGame g : rows) {
            TeamRef h = mapTeam(g.getHomeTeam(), turkish);
            TeamRef a = mapTeam(g.getAwayTeam(), turkish);
            String homeName = h != null ? h.displayName() : "home";
            String awayName = a != null ? a.displayName() : "away";
            String slug = SlugUtil.gameSlug(homeName, awayName, g.getId());
            String winnerSide = null;
            if (g.getHomeTotal() != null && g.getAwayTotal() != null) {
                if (g.getHomeTotal() > g.getAwayTotal()) winnerSide = "home";
                else if (g.getAwayTotal() > g.getHomeTotal()) winnerSide = "away";
            }
            out.add(new H2hGameView(
                    g.getId(), slug, g.getStartAt(),
                    g.getStatusShort(),
                    messages.statusText(g.getStatusShort(), g.getStatusLong(), turkish),
                    h, a,
                    g.getHomeTotal(), g.getAwayTotal(),
                    winnerSide
            ));
        }
        return out;
    }

    private List<StandingsGroup> mapStandings(VolleyballGame game, boolean turkish) {
        if (game.getLeague() == null || game.getSeason() == null) return List.of();
        List<VolleyballStanding> rows = standingRepo.findByLeagueAndSeason(
                game.getLeague().getId(), game.getSeason());
        if (rows.isEmpty()) return List.of();

        Map<String, List<StandingRow>> byGroup = new TreeMap<>();
        Map<String, String> stageByGroup = new TreeMap<>();
        for (VolleyballStanding s : rows) {
            String gname = s.getGroupName() == null ? "" : s.getGroupName();
            byGroup.computeIfAbsent(gname, k -> new ArrayList<>())
                    .add(new StandingRow(
                            s.getPosition(),
                            mapTeam(s.getTeam(), turkish),
                            s.getGamesPlayed(),
                            s.getWon(),
                            s.getLost(),
                            s.getWonPercentage(),
                            s.getSetsFor(),
                            s.getSetsAgainst(),
                            (s.getSetsFor() != null && s.getSetsAgainst() != null)
                                    ? (s.getSetsFor() - s.getSetsAgainst()) : null,
                            s.getPoints(),
                            s.getForm(),
                            s.getDescription()
                    ));
            stageByGroup.putIfAbsent(gname, s.getStage());
        }
        List<StandingsGroup> out = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<StandingRow>> e : byGroup.entrySet()) {
            out.add(new StandingsGroup(e.getKey(), stageByGroup.get(e.getKey()), e.getValue()));
        }
        return out;
    }

    private static String bdToString(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}

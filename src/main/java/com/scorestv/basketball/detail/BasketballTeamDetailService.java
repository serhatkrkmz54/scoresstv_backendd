package com.scorestv.basketball.detail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.basketball.BasketballMessages;
import com.scorestv.basketball.BasketballSeasonNormalizer;
import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStat;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStatRepository;
import com.scorestv.basketball.domain.BasketballStanding;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamLeagueSeasonRepository;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.basketball.domain.BasketballTeamSeasonStat;
import com.scorestv.basketball.domain.BasketballTeamSeasonStatRepository;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.FixtureItem;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.HomeAwayBlock;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.LeagueRef;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.OverviewBlock;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.RosterPlayer;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.SeasonSummary;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.StandingPosition;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.StatisticsBlock;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.TeamHero;
import com.scorestv.basketball.web.dto.BasketballTeamDetailResponse.TeamRef;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basketbol takim detay sayfasi orkestrasyon servisi.
 *
 * <p>Endpoint cagrildiginda burayi cagirir; servis:
 * <ol>
 *   <li>Slug → takim id resolve eder ({@link SlugUtil#extractTeamId}).
 *   <li>Lig + sezon resolve eder (param yoksa cold-start ile games tablosundan).
 *   <li>{@link BasketballSeasonNormalizer} ile sezonu lige uyarlar.
 *   <li>Lazy sync tetikler ({@link BasketballTeamDetailLazySync}).
 *   <li>DB'den hero + roster + recent/upcoming + stats + standings cekip
 *       lokalize eder (TR'de nameTr fallback).
 *   <li>Tum veriyi {@link BasketballTeamDetailResponse}'da doner.
 * </ol>
 *
 * <p>Cache: {@code BASKETBALL_TEAM_DETAIL::{slug}:{league}:{season}:{lang}}.
 * Lazy sync sonrasi async refresh tamamlaninca {@link #evictCache} cagrilarak
 * bir sonraki istek taze veri alir (stale-while-revalidate).
 */
@Service
public class BasketballTeamDetailService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamDetailService.class);

    private static final String CACHE_NAME = "BASKETBALL_TEAM_DETAIL";
    private static final int RECENT_LIMIT = 20;
    private static final int UPCOMING_LIMIT = 20;
    private static final int ROSTER_LIMIT = 40;

    /** Lokal JSON mapper — global ObjectMapper DI cakismasini engellemek icin. */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final BasketballTeamRepository teamRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballGameRepository gameRepo;
    private final BasketballPlayerSeasonStatRepository playerStatRepo;
    private final BasketballStandingRepository standingRepo;
    private final BasketballTeamSeasonStatRepository teamStatRepo;
    private final BasketballTeamLeagueSeasonRepository junctionRepo;
    private final BasketballTeamDetailLazySync lazySync;
    private final BasketballMessages messages;
    private final BasketballTeamDetailService self;

    public BasketballTeamDetailService(
            BasketballTeamRepository teamRepo,
            BasketballLeagueRepository leagueRepo,
            BasketballGameRepository gameRepo,
            BasketballPlayerSeasonStatRepository playerStatRepo,
            BasketballStandingRepository standingRepo,
            BasketballTeamSeasonStatRepository teamStatRepo,
            BasketballTeamLeagueSeasonRepository junctionRepo,
            BasketballTeamDetailLazySync lazySync,
            BasketballMessages messages,
            @Lazy BasketballTeamDetailService self) {
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.gameRepo = gameRepo;
        this.playerStatRepo = playerStatRepo;
        this.standingRepo = standingRepo;
        this.teamStatRepo = teamStatRepo;
        this.junctionRepo = junctionRepo;
        this.lazySync = lazySync;
        this.messages = messages;
        this.self = self;
    }

    /**
     * Public endpoint methodu. Slug + opsiyonel sezon + dil ile cagrilir.
     * Lazy sync tetikler, sonra cache'ten/DB'den okuyarak full DTO doner.
     */
    public BasketballTeamDetailResponse getBySlug(String slug, String season,
                                                    boolean turkish) {
        Long teamId = SlugUtil.extractTeamId(slug);
        if (teamId == null) throw ApiException.notFound("Takim bulunamadi");

        BasketballTeam team = teamRepo.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi"));

        // Auto-cover on visit — sonraki cron tazelemelerinde dahil olsun
        if (!team.isCovered()) {
            team.setCovered(true);
            teamRepo.save(team);
        }

        // Lig + sezon resolve
        LeagueSeasonPair lsp = resolveLeagueAndSeason(teamId, season, team);
        if (lsp == null) {
            // Hicbir lig/sezon yok — minimal hero ile bos sayfa
            return emptyResponse(team, turkish);
        }

        BasketballLeague league = leagueRepo.findById(lsp.leagueId).orElse(null);
        String normalizedSeason = league != null
                ? BasketballSeasonNormalizer.normalize(lsp.season, league.getSeasonsJson())
                : lsp.season;

        // Lazy sync tetikle — async fire-and-forget, beklemiyoruz
        try {
            lazySync.refreshIfNeeded(teamId, lsp.leagueId, normalizedSeason);
        } catch (Exception e) {
            log.debug("Lazy sync hata team={}: {}", teamId, e.toString());
        }

        return self.loadCached(teamId, lsp.leagueId, normalizedSeason, turkish);
    }

    /** Force refresh — pull-to-refresh / admin tetikleme. Cache evict + lazy sync. */
    public BasketballTeamDetailResponse forceRefresh(String slug, String season,
                                                       boolean turkish) {
        Long teamId = SlugUtil.extractTeamId(slug);
        if (teamId == null) throw ApiException.notFound("Takim bulunamadi");

        BasketballTeam team = teamRepo.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi"));

        LeagueSeasonPair lsp = resolveLeagueAndSeason(teamId, season, team);
        if (lsp == null) return emptyResponse(team, turkish);

        BasketballLeague league = leagueRepo.findById(lsp.leagueId).orElse(null);
        String normalizedSeason = league != null
                ? BasketballSeasonNormalizer.normalize(lsp.season, league.getSeasonsJson())
                : lsp.season;

        // Cache evict her iki dil icin
        self.evictCache(teamId, lsp.leagueId, normalizedSeason, true);
        self.evictCache(teamId, lsp.leagueId, normalizedSeason, false);

        lazySync.forceRefresh(teamId, lsp.leagueId, normalizedSeason);

        return self.loadCached(teamId, lsp.leagueId, normalizedSeason, turkish);
    }

    // ================================================================
    // Cached read + evict
    // ================================================================

    @Cacheable(value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#teamId, #leagueId, #season, #turkish)")
    @Transactional(readOnly = true)
    public BasketballTeamDetailResponse loadCached(Long teamId, Long leagueId,
                                                     String season, boolean turkish) {
        BasketballTeam team = teamRepo.findById(teamId)
                .orElseThrow(() -> ApiException.notFound("Takim bulunamadi"));
        BasketballLeague league = leagueRepo.findById(leagueId).orElse(null);
        return build(team, league, season, turkish);
    }

    @CacheEvict(value = CACHE_NAME,
            key = "T(java.util.Objects).hash(#teamId, #leagueId, #season, #turkish)")
    public void evictCache(Long teamId, Long leagueId, String season, boolean turkish) {
        // body bos
    }

    // ================================================================
    // Build DTO
    // ================================================================

    private BasketballTeamDetailResponse build(BasketballTeam team, BasketballLeague league,
                                                 String season, boolean turkish) {
        TeamHero hero = mapHero(team, turkish);
        LeagueRef leagueRef = mapLeagueRef(league, turkish);
        List<String> availableSeasons = listAvailableSeasons(team.getId());

        // Recent + upcoming maclar (tum ligler kapsayalim — sezon filtreli olsun)
        var recent = gameRepo.findRecentByTeam(team.getId(),
                league != null ? league.getId() : null,
                season,
                PageRequest.of(0, RECENT_LIMIT));
        var upcoming = gameRepo.findUpcomingByTeam(team.getId(),
                league != null ? league.getId() : null,
                season,
                PageRequest.of(0, UPCOMING_LIMIT));

        List<FixtureItem> recentItems = recent.stream()
                .map(g -> mapFixture(g, turkish)).toList();
        List<FixtureItem> upcomingItems = upcoming.stream()
                .map(g -> mapFixture(g, turkish)).toList();

        // Roster — sezon stat tablosundan; bossa empty
        List<RosterPlayer> roster = List.of();
        if (league != null) {
            var statRows = playerStatRepo.findRosterByTeamLeagueSeason(
                    team.getId(), league.getId(), season);
            roster = statRows.stream()
                    .limit(ROSTER_LIMIT)
                    .map(s -> mapRosterPlayer(s, turkish))
                    .toList();
        }

        // Statistics block (sezon ozet)
        StatisticsBlock statistics = league != null
                ? mapStatistics(team.getId(), league.getId(), season)
                : null;

        // Standings position
        StandingPosition standingsPosition = league != null
                ? mapStandingPosition(team.getId(), league.getId(), season, turkish)
                : null;

        // Overview
        FixtureItem lastGame = recentItems.isEmpty() ? null : recentItems.get(0);
        FixtureItem nextGame = upcomingItems.isEmpty() ? null : upcomingItems.get(0);
        SeasonSummary summary = statistics != null
                ? new SeasonSummary(
                        statistics.wins(),
                        statistics.loses(),
                        statistics.winPercentage(),
                        statistics.pointsForAvg(),
                        statistics.pointsAgainstAvg())
                : null;
        OverviewBlock overview = new OverviewBlock(lastGame, nextGame, summary);

        return new BasketballTeamDetailResponse(
                hero,
                leagueRef,
                season,
                availableSeasons,
                overview,
                roster,
                recentItems,
                upcomingItems,
                statistics,
                standingsPosition,
                team.getLastStatsSyncedAt()
        );
    }

    private TeamHero mapHero(BasketballTeam t, boolean turkish) {
        String displayName = turkish && t.getNameTr() != null && !t.getNameTr().isBlank()
                ? t.getNameTr() : t.getName();
        return new TeamHero(
                t.getId(),
                t.getName(),
                displayName,
                t.getLogo(),
                t.getCode(),
                t.getFounded(),
                t.isNational(),
                t.getCountryName(),
                t.getCountryCode(),
                t.getCountryFlag(),
                t.getVenueName(),
                t.getVenueCity(),
                t.getVenueCapacity(),
                t.getSlug() != null ? t.getSlug()
                        : SlugUtil.teamSlug(displayName, t.getId())
        );
    }

    private LeagueRef mapLeagueRef(BasketballLeague l, boolean turkish) {
        if (l == null) return null;
        String displayName = turkish && l.getNameTr() != null && !l.getNameTr().isBlank()
                ? l.getNameTr() : l.getName();
        return new LeagueRef(
                l.getId(),
                l.getName(),
                displayName,
                l.getLogo(),
                SlugUtil.leagueSlug(displayName, l.getId()),
                messages.leagueType(l.getType(), turkish)
        );
    }

    private FixtureItem mapFixture(BasketballGame g, boolean turkish) {
        return new FixtureItem(
                g.getId(),
                SlugUtil.gameSlug(g.getHomeTeam().getName(), g.getAwayTeam().getName(),
                        g.getId()),
                g.getStartAt(),
                g.getStatusShort(),
                g.getStatusLong(),
                g.getStatusLong(), // statusText caller'da dil bilinciyle dolar
                g.getStage(),
                g.getWeek(),
                mapTeamRef(g.getHomeTeam(), turkish),
                mapTeamRef(g.getAwayTeam(), turkish),
                g.getHomeTotal(),
                g.getAwayTotal()
        );
    }

    private TeamRef mapTeamRef(BasketballTeam t, boolean turkish) {
        if (t == null) return null;
        String displayName = turkish && t.getNameTr() != null && !t.getNameTr().isBlank()
                ? t.getNameTr() : t.getName();
        return new TeamRef(
                t.getId(),
                t.getName(),
                displayName,
                t.getLogo(),
                t.getSlug() != null ? t.getSlug()
                        : SlugUtil.teamSlug(displayName, t.getId())
        );
    }

    private RosterPlayer mapRosterPlayer(BasketballPlayerSeasonStat s, boolean turkish) {
        BasketballPlayer p = s.getPlayer();
        String displayName = p.getName();
        return new RosterPlayer(
                p.getId(),
                p.getName(),
                displayName,
                p.getPhoto(),
                p.getPosition(),
                p.getJerseyNumber(),
                p.getHeightCm(),
                p.getWeightKg(),
                p.getNationality(),
                p.getSlug() != null ? p.getSlug()
                        : SlugUtil.playerSlug(p.getFirstName(), p.getLastName(),
                                displayName, p.getId())
        );
    }

    private StatisticsBlock mapStatistics(Long teamId, Long leagueId, String season) {
        var row = teamStatRepo.findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season)
                .orElse(null);
        if (row == null) return null;

        HomeAwayBlock home = parseHomeAway(row, "home");
        HomeAwayBlock away = parseHomeAway(row, "away");

        BigDecimal diffAvg = null;
        if (row.getPointsForAvg() != null && row.getPointsAgainstAvg() != null) {
            diffAvg = row.getPointsForAvg().subtract(row.getPointsAgainstAvg())
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new StatisticsBlock(
                row.getGamesPlayed(),
                row.getWins(),
                row.getLoses(),
                row.getWinPercentage(),
                row.getPointsForTotal(),
                row.getPointsForAvg(),
                row.getPointsAgainstTotal(),
                row.getPointsAgainstAvg(),
                diffAvg,
                row.getLongestWinStreak(),
                row.getLongestLoseStreak(),
                row.getForm(),
                home,
                away
        );
    }

    @SuppressWarnings("unchecked")
    private HomeAwayBlock parseHomeAway(BasketballTeamSeasonStat row, String side) {
        if (row.getHomeAwayJson() == null) return null;
        try {
            Map<String, Object> root = JSON_MAPPER.readValue(
                    row.getHomeAwayJson(), new TypeReference<>() {});
            Object block = root.get(side);
            if (!(block instanceof Map<?, ?> m)) return null;
            Map<String, Object> b = (Map<String, Object>) m;
            return new HomeAwayBlock(
                    asInt(b.get("played")),
                    asInt(b.get("wins")),
                    asInt(b.get("loses")),
                    asBd(b.get("winsPct")),
                    asBd(b.get("pointsForAvg")),
                    asBd(b.get("pointsAgainstAvg"))
            );
        } catch (Exception e) {
            log.debug("home/away JSON parse hatasi: {}", e.toString());
            return null;
        }
    }

    private StandingPosition mapStandingPosition(Long teamId, Long leagueId, String season,
                                                 boolean turkish) {
        List<BasketballStanding> rows = standingRepo.findForTeam(leagueId, season, teamId);
        if (rows.isEmpty()) return null;
        BasketballStanding s = rows.get(0);
        return new StandingPosition(
                s.getPosition(),
                messages.standingGroupName(s.getGroupName(), turkish),
                s.getGamesPlayedAll(),
                s.getWonAll(),
                s.getLostAll(),
                asBd(s.getWonPercentage()),
                messages.standingDescription(s.getDescription(), turkish)
        );
    }

    private List<String> listAvailableSeasons(Long teamId) {
        // Junction'dan farkli sezonlar (ister farkli ligler) — dedup + sort
        var pairs = junctionRepo.findLeaguesForTeam(teamId);
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (Object[] arr : pairs) {
            if (arr.length >= 2 && arr[1] != null) {
                seen.put(arr[1].toString(), Boolean.TRUE);
            }
        }
        // Junction bossa games tablosundan turevle
        if (seen.isEmpty()) {
            var pairsFromGames = gameRepo.findTeamLeagueSeasonPairs(
                    teamId, PageRequest.of(0, 100));
            for (Object[] arr : pairsFromGames) {
                if (arr.length >= 2 && arr[1] != null) {
                    seen.put(arr[1].toString(), Boolean.TRUE);
                }
            }
        }
        List<String> out = new ArrayList<>(seen.keySet());
        out.sort((a, b) -> b.compareTo(a));
        return out;
    }

    /**
     * Lig + sezon resolve mantigi.
     * 1) Sezon param verildi mi? Verildiyse junction'dan en yeni lig'i bul.
     * 2) Param yoksa junction'dan en yeni (lig, sezon) cifti.
     * 3) Junction bos ise games tablosundan turevle.
     */
    private LeagueSeasonPair resolveLeagueAndSeason(Long teamId, String requestedSeason,
                                                      BasketballTeam team) {
        // Once junction
        List<Object[]> junctionPairs = junctionRepo.findLeaguesForTeam(teamId);
        for (Object[] arr : junctionPairs) {
            if (arr.length < 2 || arr[0] == null || arr[1] == null) continue;
            Long lid = ((Number) arr[0]).longValue();
            String s = arr[1].toString();
            if (requestedSeason == null || requestedSeason.isBlank()
                    || requestedSeason.equals(s)
                    // Veya year-eslesme — "2026" ile "2025-2026" gibi
                    || tailMatches(requestedSeason, s)) {
                return new LeagueSeasonPair(lid, s);
            }
        }

        // Games tablosundan fallback (en yeni eslesen)
        List<Object[]> gamePairs = gameRepo.findTeamLeagueSeasonPairs(
                teamId, PageRequest.of(0, 20));
        for (Object[] arr : gamePairs) {
            if (arr.length < 2 || arr[0] == null || arr[1] == null) continue;
            Long lid = ((Number) arr[0]).longValue();
            String s = arr[1].toString();
            if (requestedSeason == null || requestedSeason.isBlank()
                    || requestedSeason.equals(s)
                    || tailMatches(requestedSeason, s)) {
                return new LeagueSeasonPair(lid, s);
            }
        }

        return null;
    }

    private boolean tailMatches(String a, String b) {
        if (a == null || b == null) return false;
        String aTail = a.length() >= 4 ? a.substring(a.length() - 4) : a;
        String bTail = b.length() >= 4 ? b.substring(b.length() - 4) : b;
        return aTail.equals(bTail);
    }

    private BasketballTeamDetailResponse emptyResponse(BasketballTeam team, boolean turkish) {
        return new BasketballTeamDetailResponse(
                mapHero(team, turkish),
                null,
                null,
                List.of(),
                new OverviewBlock(null, null, null),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        );
    }

    private Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private BigDecimal asBd(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }

    private record LeagueSeasonPair(Long leagueId, String season) {}

    /** Get last sync time'i UTC olarak doner — Controller for cache-busting headers. */
    public Instant getLastSyncTime() {
        return Instant.now();
    }
}

package com.scorestv.basketball.detail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.scorestv.basketball.BkLeagueDto;
import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayer;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayer.Category;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayerRepository;
import com.scorestv.basketball.domain.BasketballStanding;
import com.scorestv.basketball.domain.BasketballStandingRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.seo.BasketballLeagueDetailSeoBuilder;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.CoverageInfo;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.GameSummary;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.SeasonInfo;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.StandingRow;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.StandingsGroup;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.TeamRef;
import com.scorestv.basketball.web.dto.BasketballLeagueDetailResponse.TopPlayerView;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basketbol lig detay sayfasi orkestrasyon servisi.
 *
 * <p>Endpoint cagrildiginda burayi cagirir; servis:
 * <ol>
 *   <li>Slug → lig id resolve eder ({@link SlugUtil#extractLeagueId}).
 *   <li>Lazy sync tetikler (async, beklemeyiz).
 *   <li>DB'den lig + sezonlar + standings + games + top players cekip
 *       lokalize eder ({@code name_tr}, {@code country_name_tr} fallback).
 *   <li>SEO paketi {@link BasketballLeagueDetailSeoBuilder} ile uretir.
 *   <li>Tum veriyi {@link BasketballLeagueDetailResponse}'da doner.
 * </ol>
 *
 * <p>Cache: {@code basketballLeagueDetail::{leagueId}-{season}-{lang}} — 1
 * saat. Lazy sync sonrasi cache evict edilmez; bir sonraki cagri otomatik
 * tazelenmis veriyi alir (stale-while-revalidate).
 */
@Service
public class BasketballLeagueDetailService {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueDetailService.class);

    /** Lig detayinda son maclar widget icin max satir. */
    private static final int RECENT_GAMES_LIMIT = 30;
    /** Lig detayinda yaklasan maclar widget icin max satir. */
    private static final int UPCOMING_GAMES_LIMIT = 30;

    private final BasketballLeagueRepository leagueRepo;
    private final BasketballStandingRepository standingRepo;
    private final BasketballGameRepository gameRepo;
    private final BasketballLeagueTopPlayerRepository topPlayerRepo;
    private final BasketballLeagueDetailLazySync lazySync;
    private final BasketballLeagueDetailSeoBuilder seoBuilder;
    private final MinioStorageService storage;

    /**
     * Lokal ObjectMapper — Spring DI'da custom {@code RedisConfig} mapper'i
     * primary olmadigindan global ObjectMapper bean'ina dayanmiyoruz. Sadece
     * seasons_json JSONB string'i parse etmek icin kullaniliyor.
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public BasketballLeagueDetailService(
            BasketballLeagueRepository leagueRepo,
            BasketballStandingRepository standingRepo,
            BasketballGameRepository gameRepo,
            BasketballLeagueTopPlayerRepository topPlayerRepo,
            BasketballLeagueDetailLazySync lazySync,
            BasketballLeagueDetailSeoBuilder seoBuilder,
            MinioStorageService storage) {
        this.leagueRepo = leagueRepo;
        this.standingRepo = standingRepo;
        this.gameRepo = gameRepo;
        this.topPlayerRepo = topPlayerRepo;
        this.lazySync = lazySync;
        this.seoBuilder = seoBuilder;
        this.storage = storage;
    }

    /**
     * Lig detay sayfasi response'u. Cache key locale + season dahil.
     *
     * @param slug   "nba-12" gibi URL slug
     * @param season opsiyonel ("2024-2025"); null ise league.currentSeason
     * @param lang   "tr" veya "en"
     */
    @Cacheable(
            value = "basketballLeagueDetail",
            key = "#slug + ':' + (#season != null ? #season : 'current') + ':' + #lang",
            unless = "#result == null || #result.isThin()")
    @Transactional(readOnly = true)
    public BasketballLeagueDetailResponse getDetail(String slug, String season, String lang) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gecersiz slug");
        }
        BasketballLeague league = leagueRepo.findById(leagueId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lig bulunamadi"));

        // 1) Lazy sync — async, beklemeyiz. Sezon normalize edilmis halini gonderiyoruz
        //    ki async tarafi da dogru sezonla devam etsin.
        String normalizedSeason = com.scorestv.basketball.BasketballSeasonNormalizer
                .normalize(season, league.getSeasonsJson());
        try {
            lazySync.refreshIfNeeded(leagueId, normalizedSeason);
        } catch (Exception e) {
            log.warn("Basketbol lig detay lazy sync hata id={}: {}", leagueId, e.toString());
        }

        boolean turkish = "tr".equalsIgnoreCase(lang);

        // 2) Sezonlar parse (JSONB → SeasonInfo listesi)
        List<BkLeagueDto.Season> rawSeasons = parseSeasons(league.getSeasonsJson());
        List<SeasonInfo> seasonInfos = mapSeasons(rawSeasons);

        // 3) Selected season — caller param > current. Selected'i de normalize
        //    et: bazi liglerde API "2025-2026" bazilarinda "2026" kabul eder;
        //    normalizer seasonsJson'daki gercek formati bulup donduriyor.
        String rawSelected = (season != null && !season.isBlank())
                ? season
                : league.getCurrentSeason();
        String selectedSeason = com.scorestv.basketball.BasketballSeasonNormalizer
                .normalize(rawSelected, league.getSeasonsJson());

        // 4) Coverage (selected sezon icin)
        CoverageInfo coverage = pickCoverage(rawSeasons, selectedSeason);

        // 5) Standings
        List<StandingsGroup> standings = selectedSeason != null
                ? loadStandings(leagueId, selectedSeason, turkish)
                : List.of();

        // 6) Recent + Upcoming games
        List<GameSummary> recentGames = selectedSeason != null
                ? loadGames(leagueId, selectedSeason, true)
                : List.of();
        List<GameSummary> upcomingGames = selectedSeason != null
                ? loadGames(leagueId, selectedSeason, false)
                : List.of();

        // 7) 3 kategori top players
        List<TopPlayerView> topScorers = selectedSeason != null
                ? loadTopCategory(leagueId, selectedSeason, Category.SCORERS) : List.of();
        List<TopPlayerView> topRebounders = selectedSeason != null
                ? loadTopCategory(leagueId, selectedSeason, Category.REBOUNDERS) : List.of();
        List<TopPlayerView> topAssists = selectedSeason != null
                ? loadTopCategory(leagueId, selectedSeason, Category.ASSISTS) : List.of();

        // 8) Lig basic info lokalize
        String displayName = turkish && league.getNameTr() != null && !league.getNameTr().isBlank()
                ? league.getNameTr() : league.getName();
        String displayCountry = turkish && league.getCountryNameTr() != null
                && !league.getCountryNameTr().isBlank()
                ? league.getCountryNameTr() : league.getCountryName();

        var country = new BasketballLeagueDetailResponse.Country(
                displayCountry,
                league.getCountryCode(),
                resolveImage(league.getCountryFlagKey(), league.getCountryFlag()));

        // 9) SEO
        var seo = seoBuilder.build(league, selectedSeason, lang);

        return new BasketballLeagueDetailResponse(
                league.getId(),
                league.getSlug(),
                displayName,
                league.getType(),
                resolveImage(league.getLogoKey(), league.getLogo()),
                country,
                league.getCurrentSeason(),
                selectedSeason,
                seasonInfos,
                coverage,
                standings,
                recentGames,
                upcomingGames,
                topScorers,
                topRebounders,
                topAssists,
                seo);
    }

    // ================================================================
    // Helpers
    // ================================================================

    /** seasons_json (JSONB) → ham DTO listesi. Hata olursa bos. */
    private List<BkLeagueDto.Season> parseSeasons(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("BasketballLeague seasonsJson parse hata: {}", e.getMessage());
            return List.of();
        }
    }

    /** Ham sezon listesini DTO'ya cevirir (yeni → eski). */
    private static List<SeasonInfo> mapSeasons(List<BkLeagueDto.Season> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<SeasonInfo> out = new ArrayList<>(raw.size());
        for (var s : raw) {
            if (s == null || s.season() == null) continue;
            out.add(new SeasonInfo(
                    s.season(),
                    parseDate(s.start()),
                    parseDate(s.end()),
                    Boolean.TRUE.equals(s.current()),
                    toCoverage(s.coverage())));
        }
        // Yeni → eski (season string "YYYY-YYYY" lexikografik karsilastirma)
        out.sort(Comparator.comparing(SeasonInfo::season).reversed());
        return out;
    }

    /** Verilen sezon icin coverage dondurur; bulunamazsa default (hepsi false). */
    private static CoverageInfo pickCoverage(List<BkLeagueDto.Season> raw, String season) {
        if (raw == null || season == null) return defaultCoverage();
        for (var s : raw) {
            if (season.equals(s.season())) return toCoverage(s.coverage());
        }
        return defaultCoverage();
    }

    private static CoverageInfo toCoverage(BkLeagueDto.Coverage c) {
        if (c == null) return defaultCoverage();
        boolean teams = c.games() != null && c.games().statistics() != null
                && Boolean.TRUE.equals(c.games().statistics().teams());
        boolean players = c.games() != null && c.games().statistics() != null
                && Boolean.TRUE.equals(c.games().statistics().players());
        return new CoverageInfo(
                teams,
                players,
                Boolean.TRUE.equals(c.standings()),
                Boolean.TRUE.equals(c.players()),
                Boolean.TRUE.equals(c.odds()));
    }

    private static CoverageInfo defaultCoverage() {
        return new CoverageInfo(false, false, false, false, false);
    }

    /**
     * Standings'i grup adina gore gruplar. Tek-grup liglerde groupName = "".
     */
    private List<StandingsGroup> loadStandings(Long leagueId, String season, boolean turkish) {
        List<BasketballStanding> rows = standingRepo.findByLeagueAndSeason(leagueId, season);
        if (rows.isEmpty()) return List.of();
        // Sira korumali grup haritasi
        Map<String, List<StandingRow>> byGroup = new LinkedHashMap<>();
        Map<String, String> stageByGroup = new LinkedHashMap<>();
        for (BasketballStanding s : rows) {
            String groupName = s.getGroupName() != null ? s.getGroupName() : "";
            byGroup.computeIfAbsent(groupName, k -> new ArrayList<>()).add(mapStandingRow(s));
            stageByGroup.putIfAbsent(groupName, s.getStage());
        }
        List<StandingsGroup> out = new ArrayList<>(byGroup.size());
        for (var entry : byGroup.entrySet()) {
            out.add(new StandingsGroup(
                    entry.getKey(),
                    stageByGroup.get(entry.getKey()),
                    entry.getValue()));
        }
        return out;
    }

    private StandingRow mapStandingRow(BasketballStanding s) {
        BasketballTeam t = s.getTeam();
        var teamRef = t == null ? null : new TeamRef(
                t.getId(), t.getName(), null,
                resolveImage(t.getLogoKey(), t.getLogo()));
        Integer pointsFor = s.getPointsFor();
        Integer pointsAgainst = s.getPointsAgainst();
        Integer pointsDiff = (pointsFor != null && pointsAgainst != null)
                ? pointsFor - pointsAgainst
                : null;
        return new StandingRow(
                s.getPosition(),
                teamRef,
                s.getGamesPlayedAll(),
                s.getWonAll(),
                s.getLostAll(),
                s.getWonPercentage(),
                pointsFor,
                pointsAgainst,
                pointsDiff,
                s.getForm(),
                s.getDescription(),
                s.getDescription());  // descriptionText i18n cagri seviyesinde
    }

    /** Recent (FT) veya upcoming (NS) maclari yukler. */
    private List<GameSummary> loadGames(Long leagueId, String season, boolean recent) {
        var pageable = PageRequest.of(0,
                recent ? RECENT_GAMES_LIMIT : UPCOMING_GAMES_LIMIT);
        List<BasketballGame> games = recent
                ? gameRepo.findRecentByLeagueSeason(leagueId, season, pageable)
                : gameRepo.findUpcomingByLeagueSeason(leagueId, season, pageable);
        List<GameSummary> out = new ArrayList<>(games.size());
        for (BasketballGame g : games) {
            // BasketballGame entity'sinde slug field yok — SlugUtil.gameSlug
            // ile {home}-vs-{away}-{id} formatinda uretiyoruz. Mobile'da
            // /basketball/game/{id} route'u zaten id ile calisiyor; slug
            // SEO/share linki icin.
            String gameSlug = (g.getHomeTeam() != null && g.getAwayTeam() != null)
                    ? SlugUtil.gameSlug(g.getHomeTeam().getName(),
                            g.getAwayTeam().getName(), g.getId())
                    : null;
            out.add(new GameSummary(
                    g.getId(),
                    gameSlug,
                    g.getStartAt() == null ? null : g.getStartAt().atOffset(ZoneOffset.UTC),
                    g.getStatusShort(),
                    g.getStatusLong(),
                    teamRef(g.getHomeTeam()),
                    teamRef(g.getAwayTeam()),
                    g.getHomeTotal(),
                    g.getAwayTotal(),
                    g.getStage(),
                    g.getWeek()));
        }
        return out;
    }

    private TeamRef teamRef(BasketballTeam t) {
        if (t == null) return null;
        return new TeamRef(t.getId(), t.getName(), null,
                resolveImage(t.getLogoKey(), t.getLogo()));
    }

    /** Bir kategori icin top oyuncu listesi (sirali). */
    private List<TopPlayerView> loadTopCategory(Long leagueId, String season, Category category) {
        List<BasketballLeagueTopPlayer> rows = topPlayerRepo
                .findByLeagueSeasonCategory(leagueId, season, category);
        if (rows.isEmpty()) return List.of();
        List<TopPlayerView> out = new ArrayList<>(rows.size());
        for (BasketballLeagueTopPlayer r : rows) {
            var p = r.getPlayer();
            if (p == null) continue;
            out.add(new TopPlayerView(
                    r.getPosition(),
                    p.getId(),
                    p.getName(),
                    p.getSlug(),
                    resolveImage(p.getPhotoKey(), p.getPhoto()),
                    p.getNationality(),
                    teamRef(r.getTeam()),
                    r.getValue() != null ? r.getValue().toPlainString() : null,
                    r.getGamesPlayed()));
        }
        return out;
    }

    /** MinIO key varsa CDN URL'i, yoksa ham foto URL'ini doner. */
    private String resolveImage(String key, String fallback) {
        if (key != null && !key.isBlank()) return storage.publicUrl(key);
        return fallback;
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}

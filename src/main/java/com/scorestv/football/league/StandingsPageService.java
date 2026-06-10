package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerSeasonStat;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.Standing;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.queue.SyncJobType;
import com.scorestv.football.queue.SyncQueueService;
import com.scorestv.football.sync.FixtureSyncService;
import com.scorestv.football.sync.ReferenceSyncService;
import com.scorestv.football.sync.StandingsSyncService;
import com.scorestv.football.web.PlayerPhotoResolver;
import com.scorestv.football.web.dto.BracketView;
import com.scorestv.football.web.dto.LeagueDetailResponse;
import com.scorestv.football.web.dto.StandingRow;
import com.scorestv.football.web.dto.StandingsGroup;
import com.scorestv.football.web.dto.StandingsPageResponse;
import com.scorestv.football.web.dto.StandingsPageResponse.CoverageInfo;
import com.scorestv.football.web.dto.StandingsPageResponse.LeagueMeta;
import com.scorestv.football.web.dto.StandingsPageResponse.SeasonInfo;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puan durumu sayfasi servisi — focused (lig sayfasi gibi tum widget'lari
 * yuklemiyor, sadece standings page'in ihtiyaci olan 3 widget).
 *
 * <p><b>Kendi yalin lazy sync</b> — {@link LeagueDetailLazySync} CAGRILMAZ
 * (cunku o fixture + top scorers + asssits + cards + 2 onceki sezon async
 * yapip 43sn'ye kadar bekletiyor). Burada sadece sayfaya ozgu olanlar:
 *
 * <ol>
 *   <li><b>Inline (fast)</b>: reference (lig info+sezonlar), standings —
 *       ~1-2 API cagrisi, kullanici beklemesi ~2sn</li>
 *   <li><b>Async (background)</b>: fixtures (kupa bracket icin gerekli),
 *       player_stats enqueue (top rating widget icin)</li>
 * </ol>
 *
 * <p>Ilk istek: standings dolu doner, bracket bos olabilir (fixtures async
 * yuklendigi icin). Ikinci istek (~10sn sonra): bracket + ~1dk sonra
 * topRatedPlayers da dolu.
 *
 * <p>Cache: LIVE (15sn) — canli mac varsa standings hizla degisir.
 */
@Service
public class StandingsPageService {

    private static final Logger log = LoggerFactory.getLogger(StandingsPageService.class);

    /** Standings inline sync debounce — empty oldugu surece her 5dk'da bir dene. */
    private static final Duration STANDINGS_EMPTY_RETRY = Duration.ofMinutes(5);

    /** Standings tazeleme — dolu olsa bile 1 saatte bir tazele. */
    private static final Duration STANDINGS_FRESH = Duration.ofHours(1);

    /** Reference (league info) sync — 24sa tazeleme. */
    private static final Duration REFERENCE_FRESH = Duration.ofHours(24);

    /** Fixtures async sync debounce. */
    private static final Duration FIXTURES_EMPTY_RETRY = Duration.ofMinutes(10);
    private static final Duration FIXTURES_FRESH = Duration.ofHours(6);

    /** Auto-cover/enqueue debounce — ayni (leagueId, season) icin 1sa once tekrar. */
    private static final Duration ENSURE_PLAYER_STATS_DEBOUNCE = Duration.ofHours(1);

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final StandingRepository standingRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerSeasonStatRepository playerSeasonStatRepository;
    private final CountryRepository countryRepository;
    private final ReferenceSyncService referenceSyncService;
    private final StandingsSyncService standingsSyncService;
    private final FixtureSyncService fixtureSyncService;
    private final BracketBuilder bracketBuilder;
    private final SyncQueueService queueService;
    private final FootballMessages messages;
    private final MinioStorageService storage;
    private final PlayerPhotoResolver photoResolver;
    private final StandingsPageService self;

    /** Per key son sync zamani — empty/fresh debounce icin. */
    private final Map<String, Instant> lastSyncAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAttemptAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastEnsurePlayerStatsAt = new ConcurrentHashMap<>();

    public StandingsPageService(LeagueRepository leagueRepository,
                                SeasonRepository seasonRepository,
                                StandingRepository standingRepository,
                                FixtureRepository fixtureRepository,
                                TeamRepository teamRepository,
                                PlayerSeasonStatRepository playerSeasonStatRepository,
                                CountryRepository countryRepository,
                                ReferenceSyncService referenceSyncService,
                                StandingsSyncService standingsSyncService,
                                FixtureSyncService fixtureSyncService,
                                BracketBuilder bracketBuilder,
                                SyncQueueService queueService,
                                FootballMessages messages,
                                MinioStorageService storage,
                                PlayerPhotoResolver photoResolver,
                                @Lazy StandingsPageService self) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.standingRepository = standingRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.playerSeasonStatRepository = playerSeasonStatRepository;
        this.countryRepository = countryRepository;
        this.referenceSyncService = referenceSyncService;
        this.standingsSyncService = standingsSyncService;
        this.fixtureSyncService = fixtureSyncService;
        this.bracketBuilder = bracketBuilder;
        this.queueService = queueService;
        this.messages = messages;
        this.storage = storage;
        this.photoResolver = photoResolver;
        this.self = self;
    }

    /**
     * Standings sayfasi public girisi.
     *
     * <p>Akis:
     * <ol>
     *   <li>Reference inline ensure (lig info + sezonlar)</li>
     *   <li>Selected sezon resolve</li>
     *   <li>Standings inline ensure (fast, 1 API call)</li>
     *   <li>Fixtures ASYNC ensure (cup bracket icin)</li>
     *   <li>Player stats ASYNC enqueue (top rating widget icin)</li>
     *   <li>Cache'den response yukle</li>
     * </ol>
     */
    public StandingsPageResponse getById(Long leagueId, Integer requestedSeason, boolean turkish) {
        // 1) Reference (lig + sezonlar) — INLINE, hizli (1 call, ~500ms)
        ensureReference(leagueId);

        // 2) Selected sezonu cozumle
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi."));
        List<Season> allSeasons = seasonRepository.findByLeagueIdOrderByYearDesc(leagueId);
        Integer selectedSeason = resolveSelectedSeason(league, allSeasons, requestedSeason);

        // 3) Standings — INLINE, hizli (1 API call, ~500ms)
        ensureStandings(leagueId, selectedSeason);

        // 4) Fixtures — ASYNC (bracket icin, ilk istekte bos olabilir). Cup
        //    olmayan ligler icin de fixture'lar diger sayfalardan gelir;
        //    burada sadece kupa lig icin tetikliyoruz.
        if (isCupLike(league.getType())) {
            self.ensureFixturesAsync(leagueId, selectedSeason);
        }

        // 5) Cache'den response — standings + bracket + topRated yuklenir
        StandingsPageResponse response = self.loadCachedResponse(
                leagueId, requestedSeason, turkish);

        // 6) Player stats enqueue — ASYNC, debounce'lu (~1dk sonra topRated dolar)
        try {
            self.ensurePlayerStatsForLeagueTeams(leagueId, selectedSeason);
        } catch (RuntimeException ex) {
            log.warn("Player stats ensure hatasi (leagueId={} season={}): {}",
                    leagueId, selectedSeason, ex.getMessage());
        }
        return response;
    }

    /** Cache'li okuma — yalniz {@link #getById}'den self proxy ile cagrilir. */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'standings-' + #leagueId + '-' "
                + "+ (#season == null ? 'cur' : #season) "
                + "+ '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public StandingsPageResponse loadCachedResponse(Long leagueId, Integer season, boolean turkish) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi."));

        List<Season> allSeasons = seasonRepository.findByLeagueIdOrderByYearDesc(leagueId);
        Integer selectedSeason = resolveSelectedSeason(league, allSeasons, season);

        Country country = (league.getCountryName() != null)
                ? countryRepository.findByName(league.getCountryName()).orElse(null)
                : null;
        Season selected = allSeasons.stream()
                .filter(s -> s.getYear().equals(selectedSeason))
                .findFirst()
                .orElse(null);

        List<StandingsGroup> standings = loadStandings(leagueId, selectedSeason, turkish);
        BracketView bracket = bracketBuilder.build(
                league.getType(), leagueId, selectedSeason, turkish);
        List<LeagueDetailResponse.TopRatedPlayer> topRated =
                loadTopRatedPlayers(leagueId, selectedSeason, turkish);

        return new StandingsPageResponse(
                toLeagueMeta(league, country, turkish),
                selectedSeason,
                allSeasons.stream().map(StandingsPageService::toSeasonInfo).toList(),
                toCoverage(selected),
                standings,
                bracket,
                topRated);
    }

    // ============================================================
    // Inline lazy sync (lean)
    // ============================================================

    /**
     * Lig info yoksa veya 24sa eski ise referansi sync et — INLINE.
     * 1 API call (/leagues?id=X). Sezonlar + coverage flag'leri burada gelir.
     */
    private void ensureReference(Long leagueId) {
        String key = "ref:" + leagueId;
        Instant now = Instant.now();
        boolean exists = leagueRepository.existsById(leagueId);
        if (exists) {
            Instant last = lastSyncAt.get(key);
            if (last != null && last.isAfter(now.minus(REFERENCE_FRESH))) {
                return;  // taze
            }
        } else {
            // DB'de yoksa, empty-debounce ile sik cagiri yapma
            Instant attempt = lastAttemptAt.get(key);
            if (attempt != null && attempt.isAfter(now.minus(Duration.ofMinutes(2)))) {
                return;
            }
        }
        lastAttemptAt.put(key, now);
        try {
            referenceSyncService.syncOne(leagueId);
            lastSyncAt.put(key, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Reference sync basarisiz (leagueId={}): {}", leagueId, ex.getMessage());
        }
    }

    /**
     * Standings yoksa veya 1sa eski ise sync et — INLINE. 1 API call.
     */
    private void ensureStandings(Long leagueId, Integer season) {
        if (season == null) return;
        String key = "standings:" + leagueId + "-" + season;
        Instant now = Instant.now();

        boolean empty = standingRepository
                .findByLeagueIdAndSeasonOrderByRankAsc(leagueId, season).isEmpty();
        if (empty) {
            Instant attempt = lastAttemptAt.get(key);
            if (attempt != null && attempt.isAfter(now.minus(STANDINGS_EMPTY_RETRY))) {
                return;
            }
        } else {
            Instant last = lastSyncAt.get(key);
            if (last != null && last.isAfter(now.minus(STANDINGS_FRESH))) {
                return;
            }
        }
        lastAttemptAt.put(key, now);
        try {
            int written = standingsSyncService.sync(leagueId, season).rowsWritten();
            lastSyncAt.put(key, Instant.now());
            log.info("Standings inline sync: leagueId={} season={} — {} satir",
                    leagueId, season, written);
        } catch (RuntimeException ex) {
            log.warn("Standings sync basarisiz (leagueId={} season={}): {}",
                    leagueId, season, ex.getMessage());
        }
    }

    /**
     * Fixtures yoksa veya 6sa eski ise ASYNC sync et. Cup bracket icin
     * gereklidir. Ilk istek tabii ki beklemiyor — bos bracket donebilir.
     * Self proxy ile cagri (Spring @Async).
     */
    @Async
    public void ensureFixturesAsync(Long leagueId, Integer season) {
        if (season == null) return;
        String key = "fixtures:" + leagueId + "-" + season;
        Instant now = Instant.now();

        long count = fixtureRepository.countByLeagueIdAndSeason(leagueId, season);
        if (count == 0) {
            Instant attempt = lastAttemptAt.get(key);
            if (attempt != null && attempt.isAfter(now.minus(FIXTURES_EMPTY_RETRY))) {
                return;
            }
        } else {
            Instant last = lastSyncAt.get(key);
            if (last != null && last.isAfter(now.minus(FIXTURES_FRESH))) {
                return;
            }
        }
        lastAttemptAt.put(key, now);
        try {
            int written = fixtureSyncService.syncLeagueSeason(leagueId, season);
            lastSyncAt.put(key, Instant.now());
            log.info("Fixtures async sync: leagueId={} season={} — {} fixture",
                    leagueId, season, written);
        } catch (RuntimeException ex) {
            log.warn("Fixtures sync basarisiz (leagueId={} season={}): {}",
                    leagueId, season, ex.getMessage());
        }
    }

    /**
     * Ligin standings + fixtures'taki takimlari otomatik {@code covered=true}
     * yap ve {@code TEAM_PLAYER_STATS_SYNC} job'larini queue'ya at.
     */
    @Transactional
    public void ensurePlayerStatsForLeagueTeams(Long leagueId, Integer season) {
        if (season == null) return;
        String key = leagueId + "-" + season;
        Instant last = lastEnsurePlayerStatsAt.get(key);
        Instant now = Instant.now();
        if (last != null && last.isAfter(now.minus(ENSURE_PLAYER_STATS_DEBOUNCE))) {
            return;
        }

        Set<Long> teamIds = collectLeagueTeamIds(leagueId, season);
        if (teamIds.isEmpty()) {
            return;
        }
        lastEnsurePlayerStatsAt.put(key, now);

        int marked = 0;
        for (Team t : teamRepository.findAllById(teamIds)) {
            if (!t.isCovered()) {
                t.setCovered(true);
                teamRepository.save(t);
                marked++;
            }
        }

        int enqueued = 0;
        for (Long teamId : teamIds) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("teamId", teamId);
            payload.put("season", season);
            if (queueService.enqueueIfAbsent(SyncJobType.TEAM_PLAYER_STATS_SYNC,
                    payload, SyncQueueService.PRIORITY_COVERED)) {
                enqueued++;
            }
        }
        log.info("Standings page ensure: leagueId={} season={} — {} takim covered, "
                + "{} player_stats job kuyruga eklendi", leagueId, season, marked, enqueued);
    }

    private Set<Long> collectLeagueTeamIds(Long leagueId, Integer season) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Standing s : standingRepository
                .findByLeagueIdAndSeasonOrderByRankAsc(leagueId, season)) {
            if (s.getTeam() != null) ids.add(s.getTeam().getId());
        }
        for (Fixture f : fixtureRepository.findByLeagueIdAndSeason(leagueId, season)) {
            if (f.getHomeTeam() != null) ids.add(f.getHomeTeam().getId());
            if (f.getAwayTeam() != null) ids.add(f.getAwayTeam().getId());
        }
        return ids;
    }

    // ============================================================
    // Loaders
    // ============================================================

    private List<StandingsGroup> loadStandings(Long leagueId, Integer season, boolean turkish) {
        if (season == null) return List.of();
        List<Standing> rows =
                standingRepository.findByLeagueIdAndSeasonOrderByRankAsc(leagueId, season);
        if (rows.isEmpty()) return List.of();

        Map<String, List<StandingRow>> byGroup = new LinkedHashMap<>();
        for (Standing s : rows) {
            String key = s.getGroupName() == null ? "" : s.getGroupName();
            byGroup.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(toStandingRow(s, turkish));
        }
        List<StandingsGroup> groups = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<StandingRow>> e : byGroup.entrySet()) {
            String rawGroup = e.getKey().isEmpty() ? null : e.getKey();
            String translated = rawGroup == null
                    ? null
                    : messages.standingGroupName(rawGroup, turkish);
            e.getValue().sort(Comparator.comparing(
                    StandingRow::rank, Comparator.nullsLast(Comparator.naturalOrder())));
            groups.add(new StandingsGroup(rawGroup, translated, e.getValue()));
        }
        // Gruplari isim sirasinda sirala (Group A, B, C, D...). null gruplar
        // en basa (ulusal lig — tek grup, null).
        groups.sort(Comparator.comparing(
                StandingsGroup::groupName,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return groups;
    }

    private StandingRow toStandingRow(Standing s, boolean turkish) {
        Team t = s.getTeam();
        String displayedName = displayName(t, turkish);
        return new StandingRow(
                s.getRank(),
                t.getId(),
                displayedName,
                t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                SlugUtil.teamSlug(displayedName, t.getId()),
                s.getPoints(),
                s.getGoalsDiff(),
                s.getForm(),
                s.getDescription(),
                messages.standingDescription(s.getDescription(), turkish),
                s.getPlayed(),
                s.getWin(),
                s.getDraw(),
                s.getLose(),
                s.getGoalsFor(),
                s.getGoalsAgainst());
    }

    private List<LeagueDetailResponse.TopRatedPlayer> loadTopRatedPlayers(Long leagueId,
                                                                           Integer season,
                                                                           boolean turkish) {
        if (season == null) return List.of();
        List<PlayerSeasonStat> rows = playerSeasonStatRepository
                .findTopRatedByLeagueSeason(leagueId, season, 5, 20);
        if (rows.isEmpty()) return List.of();

        var photoMap = photoResolver.loadMap(
                rows.stream().map(PlayerSeasonStat::getPlayerId)
                        .filter(Objects::nonNull).toList());

        List<LeagueDetailResponse.TopRatedPlayer> views = new ArrayList<>(rows.size());
        int rank = 1;
        for (PlayerSeasonStat s : rows) {
            Team team = s.getTeam();
            String teamDisplay = team != null ? displayName(team, turkish) : null;

            String rawPosition = null;
            java.math.BigDecimal rating = null;
            java.util.Map<String, Object> stats = s.getStatsJson();
            if (stats != null && stats.get("games") instanceof java.util.Map<?, ?> games) {
                Object pos = games.get("position");
                if (pos instanceof String posStr) rawPosition = posStr;
                Object r = games.get("rating");
                if (r instanceof String rs) {
                    try { rating = new java.math.BigDecimal(rs); } catch (Exception ignored) {}
                } else if (r instanceof Number rn) {
                    rating = java.math.BigDecimal.valueOf(rn.doubleValue());
                }
            }

            Player master = photoMap.get(s.getPlayerId());
            String playerName = master != null ? master.getName()
                    : "Player#" + s.getPlayerId();
            String playerSlug = SlugUtil.playerSlug(
                    master != null ? master.getFirstname() : null,
                    master != null ? master.getLastname() : null,
                    playerName, s.getPlayerId());

            views.add(new LeagueDetailResponse.TopRatedPlayer(
                    rank++,
                    s.getPlayerId(),
                    playerName,
                    playerSlug,
                    photoResolver.photoUrl(photoMap, s.getPlayerId(), null),
                    master != null ? master.getNationality() : null,
                    master != null ? master.getAge() : null,
                    rawPosition,
                    rawPosition == null ? null : messages.playerPosition(rawPosition, turkish),
                    team != null ? team.getId() : null,
                    teamDisplay,
                    team != null && team.getLogoKey() != null
                            ? storage.publicUrl(team.getLogoKey()) : null,
                    team != null && teamDisplay != null
                            ? SlugUtil.teamSlug(teamDisplay, team.getId())
                            : null,
                    rating,
                    stats));
        }
        return views;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private LeagueMeta toLeagueMeta(League league, Country country, boolean turkish) {
        String displayLeague = displayName(league, turkish);
        String slug = SlugUtil.leagueSlug(displayLeague, league.getId());
        return new LeagueMeta(
                league.getId(),
                slug,
                displayLeague,
                messages.leagueType(league.getType(), turkish),
                league.getType(),
                league.getLogoKey() != null ? storage.publicUrl(league.getLogoKey()) : null,
                toCountryDto(country, league, turkish),
                league.getCurrentSeason());
    }

    private StandingsPageResponse.Country toCountryDto(Country country, League league,
                                                        boolean turkish) {
        String name = displayCountryName(country, league, turkish);
        String code = country != null ? country.getCode() : league.getCountryCode();
        String flag = (country != null && country.getFlagKey() != null)
                ? storage.publicUrl(country.getFlagKey())
                : league.getCountryFlagUrl();
        return new StandingsPageResponse.Country(name, code, flag);
    }

    private static Integer resolveSelectedSeason(League league, List<Season> seasons,
                                                  Integer requested) {
        if (requested != null) return requested;
        if (league.getCurrentSeason() != null) return league.getCurrentSeason();
        return seasons.isEmpty() ? null : seasons.get(0).getYear();
    }

    private static SeasonInfo toSeasonInfo(Season s) {
        return new SeasonInfo(s.getYear(), s.getStartDate(), s.getEndDate(), s.isCurrent());
    }

    private static CoverageInfo toCoverage(Season s) {
        if (s == null) return new CoverageInfo(false, false);
        return new CoverageInfo(s.isCoverageStandings(), s.isCoverageStatsPlayers());
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) return tr;
        }
        return entity.getName();
    }

    private static String displayCountryName(Country country, League league, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        if (country != null && country.getName() != null) {
            return country.getName();
        }
        return league.getCountryName();
    }

    private static boolean isCupLike(String type) {
        if (type == null) return false;
        return type.toLowerCase(Locale.ROOT).contains("cup");
    }
}

package com.scorestv.football.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureLineup;
import com.scorestv.football.domain.FixtureLineupPlayer;
import com.scorestv.football.domain.FixtureLineupPlayerRepository;
import com.scorestv.football.domain.FixtureLineupRepository;
import com.scorestv.football.domain.FixturePlayerStat;
import com.scorestv.football.domain.FixturePlayerStatRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.FixtureStatistic;
import com.scorestv.football.domain.FixtureStatisticRepository;
import com.scorestv.football.domain.Injury;
import com.scorestv.football.domain.InjuryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Prediction;
import com.scorestv.football.domain.PredictionRepository;
import com.scorestv.football.domain.Standing;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.seo.MatchDetailSeoBuilder;
import com.scorestv.football.web.PlayerPhotoResolver;
import com.scorestv.football.web.dto.EventSummary;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.H2hFixtureView;
import com.scorestv.football.web.dto.InjuryGroup;
import com.scorestv.football.web.dto.LineupView;
import com.scorestv.football.web.dto.MatchDetailResponse;
import com.scorestv.football.web.dto.MatchSeoResponse;
import com.scorestv.football.web.dto.PlayerStatGroup;
import com.scorestv.football.web.dto.PlayerStatView;
import com.scorestv.football.web.dto.PredictionView;
import com.scorestv.football.web.dto.StandingRow;
import com.scorestv.football.web.dto.StandingsGroup;
import com.scorestv.football.web.dto.StatisticView;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tek bir maçın detay yanıtını ({@link MatchDetailResponse}) üretir.
 *
 * <p>Phase 1 yalnız maç özeti + stadyum + lig referansı + hakem + tam SEO
 * paketi döndürür. Sonraki fazlar (events, lineups, statistics, playerStats,
 * headToHead, standings, injuries, predictions) bu yanıta ek alan olarak
 * ekleyecek — mevcut alanlar değişmez.
 *
 * <p>Yanıt 30 saniyelik Redis cache'inde tutulur — canlı maçlarda WebSocket
 * güncellemeleri ayrıca push edileceği için bu kabul edilebilir gecikme.
 */
@Service
public class MatchDetailService {

    /** Henüz başlamamış maç durumları — bunlar için olay/istatistik vs. yüklenmez. */
    private static final Set<String> NOT_STARTED = Set.of("NS", "TBD");

    /**
     * API-Football'un istatistik döndürdüğü standart tip sıralaması.
     * Bu sıraya uyan tipler önce, bilinmeyen yeni tipler (varsa) sona eklenir.
     */
    private static final List<String> STAT_ORDER = List.of(
            "Shots on Goal", "Shots off Goal", "Total Shots", "Blocked Shots",
            "Shots insidebox", "Shots outsidebox",
            "Fouls", "Corner Kicks", "Offsides", "Ball Possession",
            "Yellow Cards", "Red Cards", "Goalkeeper Saves",
            "Total passes", "Passes accurate", "Passes %",
            "expected_goals");

    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository eventRepository;
    private final FixtureLineupRepository lineupRepository;
    private final FixtureLineupPlayerRepository lineupPlayerRepository;
    private final FixtureStatisticRepository statisticRepository;
    private final FixturePlayerStatRepository playerStatRepository;
    private final StandingRepository standingRepository;
    private final InjuryRepository injuryRepository;
    private final PredictionRepository predictionRepository;
    private final CountryRepository countryRepository;
    private final com.scorestv.broadcasts.BroadcastsService broadcastsService;
    private final FootballMessages messages;
    private final MinioStorageService storage;
    private final MatchDetailSeoBuilder seoBuilder;
    private final PlayerPhotoResolver photoResolver;
    private final MatchDetailLazySync lazySync;
    private final com.scorestv.football.league.BracketBuilder bracketBuilder;
    private final com.scorestv.bilyoner.BilyonerOddsService bilyonerOddsService;
    /**
     * Kendi proxy referansı — {@code loadCachedResponse}'a self-invocation
     * yerine proxy üstünden çağırmak için. Aksi halde Spring'in @Cacheable
     * ve @Transactional advice'ları DEVREYE GİRMEZ (Spring AOP proxy bypass).
     * {@code @Lazy} sirküler bağımlılık riskini ortadan kaldırır.
     */
    private final MatchDetailService self;

    public MatchDetailService(FixtureRepository fixtureRepository,
                              FixtureEventRepository eventRepository,
                              FixtureLineupRepository lineupRepository,
                              FixtureLineupPlayerRepository lineupPlayerRepository,
                              FixtureStatisticRepository statisticRepository,
                              FixturePlayerStatRepository playerStatRepository,
                              StandingRepository standingRepository,
                              InjuryRepository injuryRepository,
                              PredictionRepository predictionRepository,
                              CountryRepository countryRepository,
                              com.scorestv.broadcasts.BroadcastsService broadcastsService,
                              FootballMessages messages,
                              MinioStorageService storage,
                              MatchDetailSeoBuilder seoBuilder,
                              PlayerPhotoResolver photoResolver,
                              MatchDetailLazySync lazySync,
                              com.scorestv.football.league.BracketBuilder bracketBuilder,
                              com.scorestv.bilyoner.BilyonerOddsService bilyonerOddsService,
                              @Lazy MatchDetailService self) {
        this.fixtureRepository = fixtureRepository;
        this.eventRepository = eventRepository;
        this.lineupRepository = lineupRepository;
        this.lineupPlayerRepository = lineupPlayerRepository;
        this.statisticRepository = statisticRepository;
        this.playerStatRepository = playerStatRepository;
        this.standingRepository = standingRepository;
        this.injuryRepository = injuryRepository;
        this.predictionRepository = predictionRepository;
        this.countryRepository = countryRepository;
        this.broadcastsService = broadcastsService;
        this.messages = messages;
        this.storage = storage;
        this.seoBuilder = seoBuilder;
        this.photoResolver = photoResolver;
        this.lazySync = lazySync;
        this.bracketBuilder = bracketBuilder;
        this.bilyonerOddsService = bilyonerOddsService;
        this.self = self;
    }

    /**
     * Detay endpoint'inin public girişi — orkestratör. ÖNCE eksik yan
     * modülleri (h2h/lineups/injuries/predictions/standings) ihtiyaç anında
     * çeker; SONRA cached/readOnly görünümü proxy üstünden okur.
     *
     * <p><b>Tasarım:</b> Burada {@code @Transactional} ya da {@code @Cacheable}
     * YOK. Lazy sync servisleri kendi REQUIRED transaction'larını açar; bu
     * metoda readOnly TX eklemek yazmayı engellerdi. Cache ise asıl okuma
     * metodunda — sync'ten sonra DB tam dolu, ondan sonra cache'leniyor.
     *
     * @throws ApiException 404 — maç bulunamazsa
     */
    public MatchDetailResponse getById(Long id, boolean turkish) {
        return getById(id, null, turkish, false);
    }

    /**
     * Detay endpoint'i — country parametresi ile (TV yayin filtresi).
     *
     * @param country kullanici ulke kodu (null → "TR"). Cache key'ine dahil.
     */
    public MatchDetailResponse getById(Long id, String country, boolean turkish) {
        return getById(id, country, turkish, false);
    }

    /**
     * Detay endpoint'i — force refresh destegi ile.
     *
     * <p>{@code forceRefresh=true} geldiginde:
     * <ol>
     *   <li>Redis cache evict edilir — kullanici stale "bos" cevabi gormez</li>
     *   <li>Lazy sync debounce reset — 10dk empty-debounce bypass olur</li>
     *   <li>Sync tetiklenir → DB tazelenir → readOnly view dolu cevap doner</li>
     * </ol>
     *
     * <p>Mobile pull-to-refresh / TopBar refresh butonu bu yolla calisir.
     * Normal otomatik istek {@code forceRefresh=false} ile devam eder.
     */
    public MatchDetailResponse getById(Long id, String country, boolean turkish,
                                       boolean forceRefresh) {
        if (forceRefresh) {
            // Stale "henuz gelmedi" cevabini at; debounce'i sifirla
            self.evictDetailCache(id, country, turkish);
            lazySync.resetForFixture(id);
            // INLINE — kullanici "Yenile"ye basti, fresh veri istiyor.
            // 3-4sn bekleyebilir cunku zaten manuel tetikledi.
            lazySync.ensureFor(id);
        } else {
            // FIRE-AND-FORGET — request thread'i beklemez. Cevap DB'de o
            // anda ne varsa onunla doner; eksik modüller arkada doldurulur.
            // Mobile ilk acilis sonrasi "ince" cevap algilarsa 2-5sn'de
            // silent refetch yapar. Yenilemenin zaten async oldugu varsayilir.
            lazySync.ensureForAsync(id);
        }
        // Cached/readOnly görünümü self proxy üstünden oku.
        return self.loadCachedResponse(id, country, turkish);
    }

    /**
     * Detay yanitinin cache'ini siler. Force-refresh akisinda kullanilir;
     * {@code @CacheEvict} ancak Spring proxy uzerinden cagrildiginda calisir
     * (self injection ile).
     */
    @CacheEvict(value = FootballCacheNames.LIVE,
            key = "'detail-' + #id + '-' + (#country == null ? 'TR' : #country) "
                + "+ '-' + (#turkish ? 'tr' : 'en')")
    public void evictDetailCache(Long id, String country, boolean turkish) {
        // No-op — sadece @CacheEvict advice'i icin
    }

    /**
     * Detay yanıtının cache'li + readOnly okuması. Yalnız {@link #getById}
     * tarafından SELF PROXY üzerinden çağrılır; doğrudan {@code this.}
     * çağırırsanız Spring proxy bypass olur ve cache/tx devreye girmez.
     *
     * @throws ApiException 404 — maç bulunamazsa
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'detail-' + #id + '-' + (#country == null ? 'TR' : #country) "
                + "+ '-' + (#turkish ? 'tr' : 'en')",
            // Yan modullerin TAMAMI bossa cevabi cache'leme — lazy sync henuz
            // bitmemis demektir. Boyle bir cevabi 30sn cache'lemek "veri
            // gelmiyor" hissi yaratir; cache'lemeyince bir sonraki istek
            // tazelenmis DB'yi okur. Lazy sync 10dk debounce'u ayrica
            // koruyor — backend API spam edilmez.
            unless = "#result == null || ("
                + "#result.events().isEmpty() "
                + "&& #result.lineups().isEmpty() "
                + "&& #result.statistics().isEmpty() "
                + "&& #result.playerStats().isEmpty() "
                + "&& #result.headToHead().isEmpty() "
                + "&& #result.standings().isEmpty() "
                + "&& #result.injuries().isEmpty() "
                + "&& #result.prediction() == null)")
    @Transactional(readOnly = true)
    public MatchDetailResponse loadCachedResponse(Long id, String country, boolean turkish) {
        Fixture fixture = fixtureRepository.findOneWithDetails(id)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı."));
        return toResponse(fixture, country, turkish);
    }

    /** Geriye uyumluluk — country yoksa "TR" varsayilir. */
    public MatchDetailResponse loadCachedResponse(Long id, boolean turkish) {
        return loadCachedResponse(id, null, turkish);
    }

    private MatchDetailResponse toResponse(Fixture fixture, String country, boolean turkish) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        Venue venue = fixture.getVenue();
        League league = fixture.getLeague();

        // Slug DAİMA İngilizce addan üretilir — URL'ler dilden bağımsızdır.
        String slug = SlugUtil.fixtureSlug(home.getName(), away.getName(), fixture.getId());
        String lang = turkish ? "tr" : "en";
        MatchSeoResponse seo = seoBuilder.build(fixture, lang);

        // Maç başlamadıysa olay sorgusunu hiç yapma — gereksiz DB tur.
        List<EventSummary> events = isStarted(fixture)
                ? eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixture.getId())
                        .stream().map(e -> toEventSummary(e, turkish)).toList()
                : List.of();

        // Kadrolar maçtan 20-40 dk önce açıklanır — her durumda yokla, boşsa
        // boş liste döner. (Frontend "kadro açıklanmadı" göstergesi çıkarır.)
        List<LineupView> lineups = loadLineups(fixture);

        // İstatistikler maç başladıktan sonra dolar — başlamadıysa hiç sorgu yapma.
        List<StatisticView> statistics = isStarted(fixture)
                ? loadStatistics(fixture, turkish)
                : List.of();

        // Oyuncu istatistikleri de maç başladıktan sonra dolar.
        List<PlayerStatGroup> playerStats = isStarted(fixture)
                ? loadPlayerStats(fixture)
                : List.of();

        // Bu macin oynandigi sezon — gecmis bir maca bakarsak o sezonun
        // standings + bracket bilgisi gosterilmeli (lig'in current season'i
        // farkli olabilir). Fixture'da season yoksa lig current'ina dus.
        Integer matchSeason = fixture.getSeason() != null
                ? fixture.getSeason()
                : (league != null ? league.getCurrentSeason() : null);

        // H2H ve puan durumu — maç öncesi de gösterilir; DB'de yoksa boş döner.
        // Mevcut maç dahil tüm karşılaşmalar — API ne döndürdüyse aynısı.
        List<H2hFixtureView> headToHead = loadHeadToHead(
                home.getId(), away.getId(), turkish);
        List<StandingsGroup> standings = loadStandings(league, matchSeason, turkish);

        // Sakatlık + tahmin — maç öncesi en anlamlı; DB'de yoksa boş / null.
        List<InjuryGroup> injuries = loadInjuries(fixture, turkish);
        PredictionView prediction = loadPrediction(fixture.getId(), turkish);

        // TV yayin — ulke ve dile gore kanal listesi
        var broadcasts = broadcastsService.resolveForFixture(fixture, country, turkish);

        // Kupa eleme bracket'i — lig tipi "Cup" ise dolu; lig ise null.
        // matchSeason kullaniliyor — eski sezonlarda da o sezonun bracket'i.
        var bracket = bracketBuilder.build(
                league.getType(),
                league.getId(),
                matchSeason,
                turkish);

        // Bilyoner iddaa oranları — eşleşme yoksa veya özellik kapalıysa null.
        var odds = bilyonerOddsService.forFixture(home, away, fixture.getKickoffAt());

        return new MatchDetailResponse(
                fixture.getId(),
                slug,
                messages.roundText(fixture.getRound(), turkish),
                fixture.getKickoffAt(),
                fixture.getLastSyncedAt(),
                FixtureSummary.Status.of(
                        fixture.getStatusShort(),
                        messages.statusText(
                                fixture.getStatusShort(), fixture.getStatusLong(), turkish),
                        fixture.getElapsed(),
                        fixture.getStatusExtra()),
                new FixtureSummary.Team(
                        home.getId(), displayName(home, turkish), logoUrl(home.getLogoKey()),
                        SlugUtil.teamSlug(displayName(home, turkish), home.getId())),
                new FixtureSummary.Team(
                        away.getId(), displayName(away, turkish), logoUrl(away.getLogoKey()),
                        SlugUtil.teamSlug(displayName(away, turkish), away.getId())),
                new FixtureSummary.Score(
                        fixture.getHomeGoals(),
                        fixture.getAwayGoals(),
                        _period(fixture.getScoreHtHome(), fixture.getScoreHtAway()),
                        _period(fixture.getScoreEtHome(), fixture.getScoreEtAway()),
                        _period(fixture.getScorePenHome(), fixture.getScorePenAway())),
                toVenue(fixture, venue, turkish),
                toLeagueRef(league, turkish),
                fixture.getReferee(),
                events,
                lineups,
                statistics,
                playerStats,
                headToHead,
                standings,
                injuries,
                prediction,
                broadcasts,
                bracket,
                seo,
                odds);
    }

    /** Sakatlık listesi — ev sahibi önce, deplasman sonra. Boş olabilir. */
    private List<InjuryGroup> loadInjuries(Fixture fixture, boolean turkish) {
        List<Injury> rows = injuryRepository.findByFixtureId(fixture.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Long homeId = fixture.getHomeTeam().getId();
        Long awayId = fixture.getAwayTeam().getId();

        // Player master tablodan tum foto'lari tek sorguda cek (N+1 onleme)
        var photoMap = photoResolver.loadMap(
                rows.stream().map(Injury::getPlayerId).filter(java.util.Objects::nonNull).toList());

        Map<Long, List<InjuryGroup.InjuryView>> byTeam = new HashMap<>();
        for (Injury i : rows) {
            Long tid = i.getTeam().getId();
            String photo = photoResolver.photoUrl(photoMap, i.getPlayerId(), i.getPlayerPhoto());
            byTeam.computeIfAbsent(tid, k -> new ArrayList<>())
                    .add(new InjuryGroup.InjuryView(
                            i.getPlayerId(), i.getPlayerName(), photo,
                            i.getType(), messages.injuryType(i.getType(), turkish),
                            i.getReason(), messages.injuryReason(i.getReason(), turkish)));
        }
        List<InjuryGroup> groups = new ArrayList<>(2);
        if (byTeam.containsKey(homeId)) {
            groups.add(new InjuryGroup(homeId, byTeam.get(homeId)));
        }
        if (byTeam.containsKey(awayId)) {
            groups.add(new InjuryGroup(awayId, byTeam.get(awayId)));
        }
        return groups;
    }

    /** Maç tahmini — yoksa null. */
    private PredictionView loadPrediction(Long fixtureId, boolean turkish) {
        return predictionRepository.findByFixtureId(fixtureId)
                .map(p -> toPredictionView(p, turkish))
                .orElse(null);
    }

    private PredictionView toPredictionView(Prediction p, boolean turkish) {
        PredictionView.Winner winner = (p.getWinnerTeamId() != null
                || p.getWinnerComment() != null)
                ? new PredictionView.Winner(
                        p.getWinnerTeamId(),
                        p.getWinnerComment(),
                        messages.predictionComment(p.getWinnerComment(), turkish))
                : null;
        return new PredictionView(
                winner,
                p.getWinOrDraw(),
                p.getAdvice(),
                p.getUnderOver(),
                new PredictionView.Goals(p.getGoalsHome(), p.getGoalsAway()),
                new PredictionView.Percent(
                        p.getPercentHome(), p.getPercentDraw(), p.getPercentAway()),
                new PredictionView.Comparison(
                        new PredictionView.Pair(p.getComparisonFormHome(), p.getComparisonFormAway()),
                        new PredictionView.Pair(p.getComparisonAttHome(), p.getComparisonAttAway()),
                        new PredictionView.Pair(p.getComparisonDefHome(), p.getComparisonDefAway()),
                        new PredictionView.Pair(p.getComparisonPoissonHome(), p.getComparisonPoissonAway()),
                        new PredictionView.Pair(p.getComparisonH2hHome(), p.getComparisonH2hAway()),
                        new PredictionView.Pair(p.getComparisonGoalsHome(), p.getComparisonGoalsAway()),
                        new PredictionView.Pair(p.getComparisonTotalHome(), p.getComparisonTotalAway())),
                p.getTeamsJson());  // Ham passthrough — JSONB Map
    }

    /**
     * İki takımın son 10 karşılaşması (geçmiş + canlı + gelecek), yeni → eski.
     * Mevcut maç dahil — API ne döndürdüyse aynısı listede. DB'de yoksa boş;
     * lazy sync detay isteğinde çekmeye çalışır.
     *
     * <p>Eski {@code findRecentMeetings} sadece bitmiş maçları döndürüyordu;
     * yeni karşılaşan takımlar için (örn. Tobol 2 × Taraz) widget hep boş
     * çıkıyordu. Yeni sorgu tüm statüleri kapsar.
     */
    private List<H2hFixtureView> loadHeadToHead(Long teamA, Long teamB, boolean turkish) {
        List<Fixture> meetings = fixtureRepository.findMeetings(
                teamA, teamB, PageRequest.of(0, 10));
        if (meetings.isEmpty()) {
            return List.of();
        }
        List<H2hFixtureView> views = new ArrayList<>(meetings.size());
        for (Fixture m : meetings) {
            views.add(toH2hView(m, turkish));
        }
        return views;
    }

    private H2hFixtureView toH2hView(Fixture m, boolean turkish) {
        Team home = m.getHomeTeam();
        Team away = m.getAwayTeam();
        League league = m.getLeague();
        return new H2hFixtureView(
                m.getId(),
                SlugUtil.fixtureSlug(home.getName(), away.getName(), m.getId()),
                m.getKickoffAt(),
                new H2hFixtureView.LeagueRef(
                        league.getId(),
                        displayName(league, turkish),
                        logoUrl(league.getLogoKey())),
                new H2hFixtureView.Team(
                        home.getId(), displayName(home, turkish), logoUrl(home.getLogoKey()),
                        SlugUtil.teamSlug(displayName(home, turkish), home.getId())),
                new H2hFixtureView.Team(
                        away.getId(), displayName(away, turkish), logoUrl(away.getLogoKey()),
                        SlugUtil.teamSlug(displayName(away, turkish), away.getId())),
                m.getHomeGoals(),
                m.getAwayGoals(),
                m.getStatusShort(),
                messages.statusText(m.getStatusShort(), m.getStatusLong(), turkish));
    }

    /**
     * Bu maçın liginin o sezonki puan durumu — gruplara bölünmüş. Tek
     * gruplu liglerde tek eleman; gruplu turnuvalarda (CL, EURO, Copa) her
     * grup ayrı {@link StandingsGroup}. Grup içinde {@code rank} sıralı.
     *
     * <p>{@code season} parametresi macin oynandigi sezon — gecmis maclar
     * icin lig'in current season'ı degil, fixture'in sezonu kullanilmali.
     */
    private List<StandingsGroup> loadStandings(League league, Integer season, boolean turkish) {
        if (league == null || season == null) {
            return List.of();
        }
        List<Standing> rows = standingRepository.findByLeagueIdAndSeasonOrderByRankAsc(
                league.getId(), season);
        if (rows.isEmpty()) {
            return List.of();
        }
        // Grup adına göre gruplama, ekleme sırasını koru (genelde "Group A" → "B" → ...).
        // groupName boş/null olabilir (tek gruplu lig); "" key kullan.
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
            // Grup içi: rank'e göre sırala (DB ORDER BY rank'i koruyor ama
            // gruplama sonrası emin olalım).
            e.getValue().sort(Comparator.comparing(
                    StandingRow::rank, Comparator.nullsLast(Comparator.naturalOrder())));
            groups.add(new StandingsGroup(rawGroup, translated, e.getValue()));
        }
        return groups;
    }

    private StandingRow toStandingRow(Standing s, boolean turkish) {
        Team t = s.getTeam();
        String displayedName = displayName(t, turkish);
        return new StandingRow(
                s.getRank(),
                t.getId(),
                displayedName,
                logoUrl(t.getLogoKey()),
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

    /** Oyuncu istatistiklerini yükler ve takım bazlı gruplar (home → away). */
    private List<PlayerStatGroup> loadPlayerStats(Fixture fixture) {
        List<FixturePlayerStat> rows = playerStatRepository.findByFixtureId(fixture.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Long homeId = fixture.getHomeTeam().getId();
        Long awayId = fixture.getAwayTeam().getId();

        // Tum oyuncu foto'larini tek sorguda cek
        var photoMap = photoResolver.loadMap(
                rows.stream().map(FixturePlayerStat::getPlayerId)
                        .filter(java.util.Objects::nonNull).toList());

        Map<Long, List<PlayerStatView>> byTeam = new HashMap<>();
        for (FixturePlayerStat r : rows) {
            Long teamId = r.getTeam().getId();
            byTeam.computeIfAbsent(teamId, k -> new ArrayList<>())
                    .add(toPlayerStatView(r, photoMap));
        }

        List<PlayerStatGroup> groups = new ArrayList<>(2);
        if (byTeam.containsKey(homeId)) {
            groups.add(new PlayerStatGroup(homeId, byTeam.get(homeId)));
        }
        if (byTeam.containsKey(awayId)) {
            groups.add(new PlayerStatGroup(awayId, byTeam.get(awayId)));
        }
        return groups;
    }

    private PlayerStatView toPlayerStatView(FixturePlayerStat p,
                                            Map<Long, com.scorestv.football.domain.Player> photoMap) {
        return new PlayerStatView(
                p.getPlayerId(),
                p.getPlayerName(),
                photoResolver.photoUrl(photoMap, p.getPlayerId(), p.getPlayerPhoto()),
                p.getMinutes(),
                p.getJerseyNumber(),
                p.getPosition(),
                p.getRating(),
                p.getCaptain(),
                p.getSubstitute(),
                p.getOffsides(),
                new PlayerStatView.Shots(p.getShotsTotal(), p.getShotsOn()),
                new PlayerStatView.Goals(
                        p.getGoalsTotal(), p.getGoalsConceded(),
                        p.getGoalsAssists(), p.getGoalsSaves()),
                new PlayerStatView.Passes(
                        p.getPassesTotal(), p.getPassesKey(), p.getPassesAccuracy()),
                new PlayerStatView.Tackles(
                        p.getTacklesTotal(), p.getTacklesBlocks(), p.getTacklesInterceptions()),
                new PlayerStatView.Duels(p.getDuelsTotal(), p.getDuelsWon()),
                new PlayerStatView.Dribbles(
                        p.getDribblesAttempts(), p.getDribblesSuccess(), p.getDribblesPast()),
                new PlayerStatView.Fouls(p.getFoulsDrawn(), p.getFoulsCommitted()),
                new PlayerStatView.Cards(p.getCardsYellow(), p.getCardsRed()),
                new PlayerStatView.Penalty(
                        p.getPenaltyWon(), p.getPenaltyCommitted(),
                        p.getPenaltyScored(), p.getPenaltyMissed(), p.getPenaltySaved()));
    }

    /**
     * Maç istatistiklerini yükler ve home/away karşılaştırmalı satırlar üretir.
     * Standart sıralama (Shots on Goal → Total passes → expected_goals) korunur.
     * Her satıra dile göre çevrilmiş {@code typeText} eklenir; frontend
     * çeviri sözlüğü tutmak zorunda kalmaz.
     */
    private List<StatisticView> loadStatistics(Fixture fixture, boolean turkish) {
        List<FixtureStatistic> rows = statisticRepository.findByFixtureId(fixture.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Long homeId = fixture.getHomeTeam().getId();
        Long awayId = fixture.getAwayTeam().getId();

        // Tip başına [home, away] değer çifti. LinkedHashMap → keşif sırasını korur.
        Map<String, String[]> byType = new LinkedHashMap<>();
        for (FixtureStatistic s : rows) {
            String[] pair = byType.computeIfAbsent(s.getStatType(), k -> new String[2]);
            Long teamId = s.getTeam().getId();
            if (teamId.equals(homeId)) {
                pair[0] = s.getStatValue();
            } else if (teamId.equals(awayId)) {
                pair[1] = s.getStatValue();
            }
        }

        // Önce standart sıraya göre, sonra bilinmeyen yeni tipler sonda.
        Set<String> placed = new HashSet<>();
        List<StatisticView> views = new ArrayList<>();
        for (String type : STAT_ORDER) {
            String[] pair = byType.get(type);
            if (pair != null) {
                views.add(new StatisticView(
                        type, messages.statisticType(type, turkish),
                        pair[0], pair[1]));
                placed.add(type);
            }
        }
        for (Map.Entry<String, String[]> e : byType.entrySet()) {
            if (!placed.contains(e.getKey())) {
                views.add(new StatisticView(
                        e.getKey(), messages.statisticType(e.getKey(), turkish),
                        e.getValue()[0], e.getValue()[1]));
            }
        }
        return views;
    }

    /** Maçın kadrolarını yükler — ev sahibi önce, deplasman sonra. */
    private List<LineupView> loadLineups(Fixture fixture) {
        List<FixtureLineup> lineups = lineupRepository.findByFixtureIdWithTeam(fixture.getId());
        if (lineups.isEmpty()) {
            return List.of();
        }
        // Tüm kadroların oyuncularını tek sorguda topla (N+1 önle).
        List<Long> lineupIds = lineups.stream().map(BaseEntity::getId).toList();
        List<FixtureLineupPlayer> allPlayers =
                lineupPlayerRepository.findByLineupIdInOrderByLineupIdAscSortOrderAsc(lineupIds);
        Map<Long, List<FixtureLineupPlayer>> byLineup = new HashMap<>();
        for (FixtureLineupPlayer p : allPlayers) {
            byLineup.computeIfAbsent(p.getLineup().getId(), k -> new ArrayList<>()).add(p);
        }
        // Ev sahibi önce gelsin (frontend sol/sağ sahaya yerleştirir).
        Long homeId = fixture.getHomeTeam().getId();
        lineups.sort(Comparator.comparingInt(l ->
                homeId.equals(l.getTeam().getId()) ? 0 : 1));

        List<LineupView> views = new ArrayList<>(lineups.size());
        for (FixtureLineup lineup : lineups) {
            views.add(toLineupView(
                    lineup, byLineup.getOrDefault(lineup.getId(), List.of())));
        }
        return views;
    }

    private static LineupView toLineupView(FixtureLineup lineup,
                                           List<FixtureLineupPlayer> players) {
        List<LineupView.PlayerView> startXI = new ArrayList<>();
        List<LineupView.PlayerView> subs = new ArrayList<>();
        for (FixtureLineupPlayer p : players) {
            LineupView.PlayerView view = new LineupView.PlayerView(
                    p.getPlayerId(), p.getPlayerName(), p.getJerseyNumber(),
                    p.getPosition(), p.getGrid());
            if (p.isSubstitute()) {
                subs.add(view);
            } else {
                startXI.add(view);
            }
        }
        return new LineupView(
                lineup.getTeam().getId(),
                lineup.getFormation(),
                toCoachView(lineup),
                toColorsView(lineup),
                lineup.getAnnouncedAt(),
                startXI, subs);
    }

    private static LineupView.Coach toCoachView(FixtureLineup lineup) {
        if (lineup.getCoachId() == null && lineup.getCoachName() == null) {
            return null;
        }
        return new LineupView.Coach(
                lineup.getCoachId(), lineup.getCoachName(), lineup.getCoachPhoto());
    }

    private static LineupView.TeamColors toColorsView(FixtureLineup lineup) {
        boolean anyColor = lineup.getPlayerColorPrimary() != null
                || lineup.getPlayerColorNumber() != null
                || lineup.getPlayerColorBorder() != null
                || lineup.getGkColorPrimary() != null
                || lineup.getGkColorNumber() != null
                || lineup.getGkColorBorder() != null;
        if (!anyColor) {
            return null;
        }
        return new LineupView.TeamColors(
                new LineupView.ColorSet(
                        lineup.getPlayerColorPrimary(),
                        lineup.getPlayerColorNumber(),
                        lineup.getPlayerColorBorder()),
                new LineupView.ColorSet(
                        lineup.getGkColorPrimary(),
                        lineup.getGkColorNumber(),
                        lineup.getGkColorBorder()));
    }

    /**
     * Olay entity'sini DTO'ya çevirir; type/detail için dil çevirileri eklenir.
     * Team lazy proxy; sadece id çekilir.
     */
    private EventSummary toEventSummary(FixtureEvent e, boolean turkish) {
        Long teamId = e.getTeam() != null ? e.getTeam().getId() : null;
        return new EventSummary(
                e.getTimeElapsed(), e.getTimeExtra(),
                teamId,
                e.getType(), messages.eventType(e.getType(), turkish),
                e.getDetail(), messages.eventDetail(e.getDetail(), turkish),
                e.getComments(),
                e.getPlayerId(), e.getPlayerName(),
                e.getAssistId(), e.getAssistName());
    }

    /** Maç başladı mı? NS/TBD ise henüz başlamadı; aksi halde olay olabilir. */
    private static boolean isStarted(Fixture fixture) {
        String s = fixture.getStatusShort();
        return s != null && !NOT_STARTED.contains(s);
    }

    /**
     * Venue DTO'su üretir. Önce {@link Venue} FK entity tercih edilir
     * (kapasite + zemin + name_tr içerir); yoksa fixture'ın inline
     * {@code venueName}/{@code venueCity} fallback'i kullanılır (API
     * {@code venue.id} null gönderdiğinde olur — örn. UEFA CL).
     */
    private MatchDetailResponse.Venue toVenue(Fixture fixture, Venue venue, boolean turkish) {
        if (venue != null) {
            return new MatchDetailResponse.Venue(
                    venue.getId(),
                    displayName(venue, turkish),
                    venue.getCity(),
                    venue.getCapacity(),
                    messages.surface(venue.getSurface(), turkish));
        }
        // Inline fallback
        if (fixture.getVenueName() != null && !fixture.getVenueName().isBlank()) {
            return new MatchDetailResponse.Venue(
                    null,
                    fixture.getVenueName(),
                    fixture.getVenueCity(),
                    null,
                    null);
        }
        return null;
    }

    private MatchDetailResponse.LeagueRef toLeagueRef(League league, boolean turkish) {
        // Ülke adı + bayrağını countries tablosundan çöz — name_tr ve flag_key'i
        // kullanmak için. Lig adresine name_tr kopyalamak yerine tek-kaynak.
        Country country = (league.getCountryName() != null)
                ? countryRepository.findByName(league.getCountryName()).orElse(null)
                : null;
        String countryName = (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank())
                ? country.getNameTr()
                : league.getCountryName();
        String countryFlag = (country != null && country.getFlagKey() != null)
                ? storage.publicUrl(country.getFlagKey())
                : null;
        return new MatchDetailResponse.LeagueRef(
                league.getId(),
                displayName(league, turkish),
                messages.leagueType(league.getType(), turkish),
                logoUrl(league.getLogoKey()),
                countryName,
                countryFlag,
                league.getCurrentSeason());
    }

    private String logoUrl(String key) {
        return key != null ? storage.publicUrl(key) : null;
    }

    /** Bir periyot skoru — her iki taraf da null ise period gonderme. */
    private static FixtureSummary.Score.Period _period(Integer home, Integer away) {
        if (home == null && away == null) return null;
        return new FixtureSummary.Score.Period(home, away);
    }

    /** Dil "tr" ise ve Türkçe karşılığı girilmişse Türkçe ad; aksi halde İngilizce. */
    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }
}

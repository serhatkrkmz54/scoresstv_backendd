package com.scorestv.football.league;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiQuotaTracker;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import com.scorestv.football.domain.LeagueTopPlayerRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.sync.FixtureSyncService;
import com.scorestv.football.sync.ReferenceSyncService;
import com.scorestv.football.sync.StandingsSyncService;
import com.scorestv.football.sync.TopPlayersSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Lig detay endpoint'i icin ihtiyac aninda sync orkestratoru.
 *
 * <p>Davranis:
 * <ul>
 *   <li>Lig DB'de yoksa /leagues?id=X cagrisi ile ligi + tum sezonlarini ceker</li>
 *   <li>Secili sezon ile son 3 sezon icin: standings + fixtures + top players
 *       (coverage bayraklarina gore) ensure eder</li>
 *   <li>Eskiden secili sezon gelmis ama daha eski sezona kullanici tikladiysa
 *       ona da ensure</li>
 *   <li>Empty-debounce 10 dk + freshness pencereleri (standings 1sa, top 6sa,
 *       fixtures 6sa, leagueInfo 24sa)</li>
 * </ul>
 *
 * <p>Transactional DEGIL — alttaki sync servisleri kendi tx'lerini acar.
 */
@Service
public class LeagueDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(LeagueDetailLazySync.class);

    /** Empty-debounce: API bos donduyse veya istek hatasi alindiysa tekrar penceresi. */
    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);

    /** Aynı lig bu pencerede tetiklendiyse yeniden backfill açılmaz (bot guard). */
    private static final Duration RECENT_ENSURE_GUARD = Duration.ofSeconds(30);

    /** Lig info (countries/coverage) tazeleme: API gunde birkac kez guncellenir. */
    private static final Duration FRESH_LEAGUE_INFO = Duration.ofHours(24);

    /** Standings tazeleme: API saatlik. */
    private static final Duration FRESH_STANDINGS = Duration.ofHours(1);

    /** Fixtures tazeleme: live olan sezon icin 1sa, eski sezonlar icin 24sa. */
    private static final Duration FRESH_FIXTURES_CURRENT = Duration.ofHours(1);
    private static final Duration FRESH_FIXTURES_OLD = Duration.ofHours(24);

    /**
     * Top scorers/assists/cards tazeleme. Gol krali tablosu her maticten sonra
     * degisir; standings ile ayni hizda tutmak icin guncel sezon kisa (30dk),
     * biten sezonlar artik degismedigi icin uzun (24sa).
     */
    private static final Duration FRESH_TOP_PLAYERS_CURRENT = Duration.ofMinutes(30);
    private static final Duration FRESH_TOP_PLAYERS_OLD = Duration.ofHours(24);

    /**
     * Inline sync edilecek sezon sayisi (current). Bunun otesindeki onceki
     * sezonlar async background thread'de doldurulur — kullanici sezon
     * dropdown'una tikladiginda DB'de hazir olur.
     */
    private static final int INLINE_SEASON_COUNT = 1;

    /** Async pre-fetch icin onceki sezon sayisi (current sonrasi N onceki). */
    private static final int ASYNC_PREFETCH_SEASON_COUNT = 2;

    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final StandingRepository standingRepository;
    private final FixtureRepository fixtureRepository;
    private final LeagueTopPlayerRepository topPlayerRepository;

    private final ReferenceSyncService referenceSyncService;
    private final StandingsSyncService standingsSyncService;
    private final FixtureSyncService fixtureSyncService;
    private final TopPlayersSyncService topPlayersSyncService;

    /**
     * Self proxy — {@link #ensureForSeasonAsync} async cagriya proxy
     * uzerinden gitmeli. Direkt {@code this.} self-invocation Spring AOP
     * advice'larini bypass eder ve metod senkron calisir.
     */
    private final LeagueDetailLazySync self;

    private final Map<String, Instant> lastSuccessfulSync = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastAttempt = new ConcurrentHashMap<>();

    /**
     * Bu instance suresinde "zaten covered" oldugu gozlemlenen lig id'leri.
     * Aynacak lig cogu zaman covered=true'ya gecisten sonra her ziyarette
     * tekrar UPDATE'lenmemeli; bu set hizli yol saglar.
     */
    private final java.util.Set<Long> knownCoveredLeagues =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 429 cooldown durumu — cooldown aktifken hiç backfill başlatma. */
    private final ApiQuotaTracker quotaTracker;
    /** Lig-bazlı son tetik zamanı — {@link #RECENT_ENSURE_GUARD} için. */
    private final Map<Long, Instant> recentlyEnsured = new ConcurrentHashMap<>();

    /**
     * Ağır backfill (standings/fixtures/top players + sezon prefetch) için ÖZEL
     * sınırlı havuz. Eskiden request thread'i (senkron ensureFor) / stv-async
     * kullanılıyordu; bot seli altında bunlar dolup 504'e yol açabiliyordu. İzole;
     * kuyruk dolunca {@link ThreadPoolExecutor.DiscardPolicy} ile fazlası düşürülür.
     * Daemon.
     */
    private final ExecutorService lazyExecutor = new ThreadPoolExecutor(
            2, 6, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "stv-league-lazy-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardPolicy());

    @PreDestroy
    void shutdownLazyExecutor() {
        lazyExecutor.shutdown();
    }

    public LeagueDetailLazySync(LeagueRepository leagueRepository,
                                SeasonRepository seasonRepository,
                                StandingRepository standingRepository,
                                FixtureRepository fixtureRepository,
                                LeagueTopPlayerRepository topPlayerRepository,
                                ReferenceSyncService referenceSyncService,
                                StandingsSyncService standingsSyncService,
                                FixtureSyncService fixtureSyncService,
                                TopPlayersSyncService topPlayersSyncService,
                                ApiQuotaTracker quotaTracker,
                                @Lazy LeagueDetailLazySync self) {
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.standingRepository = standingRepository;
        this.fixtureRepository = fixtureRepository;
        this.topPlayerRepository = topPlayerRepository;
        this.referenceSyncService = referenceSyncService;
        this.standingsSyncService = standingsSyncService;
        this.fixtureSyncService = fixtureSyncService;
        this.topPlayersSyncService = topPlayersSyncService;
        this.quotaTracker = quotaTracker;
        this.self = self;
    }

    /**
     * Verilen lig + (opsiyonel) secili sezon icin eksik / bayatlamis modulleri
     * sync eder. Lig DB'de hic yoksa once /leagues?id ile temel bilgi cekilir.
     *
     * @param leagueId   lig id'si
     * @param requestedSeason istenen sezon (null → ligin current'i)
     */
    public void ensureFor(Long leagueId, Integer requestedSeason) {
        // 1) Lig info: DB'de hic yoksa initial fill. DB'de var ama TUM
        // sezonlarin coverage'i false ise (V16 migration sonrasi default ya
        // da pre-coverage eski sync) → resync. Bu sayede eski kayitlar
        // otomatik onarilir, manuel sync gerekmez.
        runIfNeeded("league-info:" + leagueId, FRESH_LEAGUE_INFO,
                () -> needsLeagueInfoSync(leagueId),
                () -> referenceSyncService.syncOne(leagueId));

        // Sync sonrasi lig'i tekrar yokla.
        League league = leagueRepository.findById(leagueId).orElse(null);
        if (league == null) {
            log.warn("Lig sync sonrasinda DB'de yine yok: leagueId={}", leagueId);
            return;
        }

        // Otomatik covered isaretleme — kullanici ziyaret etti, populer say.
        // Sonraki ziyaretlerde DailyLeagueRefreshJob bu ligi otomatik tazeler.
        // ONEMLI: self proxy uzerinden cagri — direkt this.markCoveredIfNeeded
        // Spring AOP'yi bypass eder, @Transactional devre disi kalir ve save()
        // commit edilmez (eski bug — covered=true DB'ye yazilmiyordu).
        self.markCoveredIfNeeded(league);

        // 2) Sezon listesi
        List<Season> allSeasons = seasonRepository.findByLeagueIdOrderByYearDesc(leagueId);
        if (allSeasons.isEmpty()) {
            return;
        }

        // 3) INLINE sync edilecek sezon(lar) — kullanici yaniti bunlari bekler.
        // Tipik: current season + (secili sezon != current ise) o sezon.
        Integer effectiveSeason = requestedSeason != null
                ? requestedSeason
                : league.getCurrentSeason();
        Map<Integer, Season> inlineSeasons = new HashMap<>();
        for (Season s : allSeasons.subList(
                0, Math.min(INLINE_SEASON_COUNT, allSeasons.size()))) {
            inlineSeasons.put(s.getYear(), s);
        }
        if (effectiveSeason != null && !inlineSeasons.containsKey(effectiveSeason)) {
            // Secili sezon current degil → onu da inline yap (kullanici onu istiyor)
            allSeasons.stream()
                    .filter(s -> s.getYear().equals(effectiveSeason))
                    .findFirst()
                    .ifPresent(s -> inlineSeasons.put(s.getYear(), s));
        }
        for (Season season : inlineSeasons.values()) {
            ensureForSeason(league, season);
        }

        // 4) ASYNC pre-fetch — current sonrasi N onceki sezonu arka planda
        // doldur. Kullanici dropdown'a tiklayinca DB'de hazir olur. Async
        // metoda self proxy uzerinden cagiri (Spring AOP).
        List<Season> asyncSeasons = allSeasons.stream()
                .filter(s -> !inlineSeasons.containsKey(s.getYear()))
                .limit(ASYNC_PREFETCH_SEASON_COUNT)
                .toList();
        for (Season season : asyncSeasons) {
            self.ensureForSeasonAsync(league.getId(), season.getYear());
        }
    }

    /**
     * Fire-and-forget asenkron versiyonu — tum {@link #ensureFor} mantigi
     * arka planda calisir. Mobile lig sayfasi normal akista bunu cagirir;
     * cevap DB'de o anda ne varsa onunla doner, eksik moduller arkadan
     * doldurulur. Silent retry zinciri tamamlanan veriyi yakalar.
     *
     * <p>Spring AOP gerekligi: self proxy uzerinden cagri zorunlu (aksi
     * halde @Async bypass olur, metod senkron calisir).
     */
    public void ensureForAsync(Long leagueId, Integer requestedSeason) {
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return; // cooldown → tazeleme atla (DB'de zaten veri var)
        }
        final Instant now = Instant.now();
        final Instant last = recentlyEnsured.get(leagueId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return; // yakında zaten tazeledik (bot aynı sayfayı döngüde tarar)
        }
        if (recentlyEnsured.size() > 20_000) recentlyEnsured.clear();
        recentlyEnsured.put(leagueId, now);
        // ÖZEL sınırlı havuz (stv-async/request thread değil); dolunca DiscardPolicy.
        lazyExecutor.execute(() -> {
            try {
                ensureFor(leagueId, requestedSeason);
            } catch (RuntimeException ex) {
                log.warn("Async lig ensure hatasi: leagueId={} season={} — {}",
                        leagueId, requestedSeason, ex.getMessage());
            }
        });
    }

    /**
     * Bir onceki sezon icin background thread'de standings + fixtures + top
     * players ensure. {@code @Async} sayesinde request thread'i beklemez.
     * Self proxy ile cagrilmali (aksi halde Spring AOP advice'i devreye girmez).
     */
    @Async
    public void ensureForSeasonAsync(Long leagueId, Integer year) {
        try {
            League league = leagueRepository.findById(leagueId).orElse(null);
            if (league == null) return;
            Season season = seasonRepository
                    .findByLeagueIdAndYear(leagueId, year)
                    .orElse(null);
            if (season == null) return;
            ensureForSeason(league, season);
        } catch (RuntimeException ex) {
            log.warn("Async sezon prefetch hatasi: leagueId={} year={} — {}",
                    leagueId, year, ex.getMessage());
        }
    }

    /**
     * Tek bir sezon icin standings + fixtures + top scorers/assists/cards
     * ensure'lar. Coverage bayraklarina bakar — kapsam yoksa cagri yapilmaz.
     */
    private void ensureForSeason(League league, Season season) {
        Long leagueId = league.getId();
        Integer year = season.getYear();
        boolean isCurrentLeagueSeason =
                league.getCurrentSeason() != null && league.getCurrentSeason().equals(year);

        // Standings — coverage bayraginda standings true ise
        if (season.isCoverageStandings()) {
            runIfNeeded("standings:" + leagueId + "-" + year, FRESH_STANDINGS,
                    () -> standingRepository
                            .findByLeagueIdAndSeasonOrderByRankAsc(leagueId, year).isEmpty(),
                    () -> standingsSyncService.sync(leagueId, year));
        }

        // Fixtures — tum sezonun fixture'i; current sezon 1sa tazeleme, eski 24sa
        Duration fxFresh = isCurrentLeagueSeason
                ? FRESH_FIXTURES_CURRENT
                : FRESH_FIXTURES_OLD;
        runIfNeeded("fixtures:" + leagueId + "-" + year, fxFresh,
                () -> fixtureRepository.countByLeagueIdAndSeason(leagueId, year) == 0,
                () -> fixtureSyncService.syncLeagueSeason(leagueId, year));

        // Top scorers / assists / cards — her birinin coverage'i ayri.
        // Guncel sezon 30dk, biten sezon 24sa (artik degismez).
        Duration tpFresh = isCurrentLeagueSeason
                ? FRESH_TOP_PLAYERS_CURRENT
                : FRESH_TOP_PLAYERS_OLD;
        if (season.isCoverageTopScorers()) {
            runIfNeeded("topScorers:" + leagueId + "-" + year, tpFresh,
                    () -> topPlayerRepository.findByLeagueSeasonCategory(
                            leagueId, year, Category.SCORERS).isEmpty(),
                    () -> topPlayersSyncService.sync(leagueId, year, Category.SCORERS));
        }
        if (season.isCoverageTopAssists()) {
            runIfNeeded("topAssists:" + leagueId + "-" + year, tpFresh,
                    () -> topPlayerRepository.findByLeagueSeasonCategory(
                            leagueId, year, Category.ASSISTS).isEmpty(),
                    () -> topPlayersSyncService.sync(leagueId, year, Category.ASSISTS));
        }
        // Cards coverage'i tek bayrakta tutuyoruz ama 2 ayri API endpoint.
        // Yellow ve red icin ayri ensure et.
        if (season.isCoverageTopCards()) {
            runIfNeeded("topYellow:" + leagueId + "-" + year, tpFresh,
                    () -> topPlayerRepository.findByLeagueSeasonCategory(
                            leagueId, year, Category.YELLOW_CARDS).isEmpty(),
                    () -> topPlayersSyncService.sync(leagueId, year, Category.YELLOW_CARDS));
            runIfNeeded("topRed:" + leagueId + "-" + year, tpFresh,
                    () -> topPlayerRepository.findByLeagueSeasonCategory(
                            leagueId, year, Category.RED_CARDS).isEmpty(),
                    () -> topPlayersSyncService.sync(leagueId, year, Category.RED_CARDS));
        }
    }

    /**
     * Lig moduluna ozgu sync mantigi:
     * <ul>
     *   <li>DB bos → empty-debounce penceresinde degilse sync</li>
     *   <li>DB dolu + freshness verilmis → son sync penceresi gectiyse sync</li>
     *   <li>DB dolu + freshness null → initial-only, atla</li>
     * </ul>
     *
     * <p><b>Cold-start protection YOK:</b> MatchDetailLazySync'ten farkli olarak
     * burada {@code lastSuccessfulSync == null} durumunda "stub timestamp koy
     * ve atla" yapilmaz. Sebep: lig sayfasi icin covered lig sayisi azdir
     * (~30) ve her modul tek bir API cagrisidir. Restart sonrasi ilk erisim
     * mevcut DB verisini tazelemeyi tercih ederiz — kullanici "neden eski
     * veri gosteriyor?" demesin. Quota maliyeti onemsiz.
     */
    private void runIfNeeded(String key, Duration freshness,
                             BooleanSupplier dbIsEmpty, Runnable syncCall) {
        try {
            boolean empty = dbIsEmpty.getAsBoolean();
            Instant now = Instant.now();

            if (empty) {
                // Bos: empty-debounce kontrolu (10dk icinde tekrar deneme).
                Instant attempt = lastAttempt.get(key);
                if (attempt != null && attempt.isAfter(now.minus(EMPTY_RETRY))) {
                    return;
                }
            } else if (freshness != null) {
                // Dolu + tazeleme penceresi: lastSuccess null ya da bayat ise sync.
                Instant lastSuccess = lastSuccessfulSync.get(key);
                if (lastSuccess != null && lastSuccess.isAfter(now.minus(freshness))) {
                    return;  // Hala taze
                }
                // null veya bayat → sync
            } else {
                return;  // Dolu + freshness null → initial-only, atla
            }

            lastAttempt.put(key, now);
            syncCall.run();
            lastSuccessfulSync.put(key, Instant.now());
        } catch (ApiException ex) {
            log.warn("League lazy sync basarisiz ({} — API): {}", key, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("League lazy sync beklenmedik hata ({}): {}", key, ex.getMessage());
        }
    }

    /**
     * Lig info sync gerekli mi? True donerse runIfNeeded'in "DB bos"
     * branch'ine girer (cold-start protection bypass — istedigimiz).
     *
     * <p>Iki durum:
     * <ul>
     *   <li>Lig DB'de hic yok → ilk fill</li>
     *   <li>Lig var ama tum sezonlarda coverage TAMAMEN false → V16 sonrasi
     *       default kalmis ya da eski (pre-coverage) sync. Resync ederek
     *       API'den gercek bayraklari getir.</li>
     * </ul>
     */
    private boolean needsLeagueInfoSync(Long leagueId) {
        if (!leagueRepository.existsById(leagueId)) {
            return true;
        }
        List<Season> seasons = seasonRepository.findByLeagueIdOrderByYearDesc(leagueId);
        if (seasons.isEmpty()) {
            return true;  // sezonlar bos, info sync ile gelir
        }
        // En az bir sezon en az bir coverage true ise: veri saglikli, sync etme.
        for (Season s : seasons) {
            if (hasAnyCoverage(s)) {
                return false;
            }
        }
        // Tum sezonlarda hepsi false → eksik veri, sync.
        return true;
    }

    private static boolean hasAnyCoverage(Season s) {
        return s.isCoverageStandings()
                || s.isCoverageEvents()
                || s.isCoverageLineups()
                || s.isCoverageStatsFixtures()
                || s.isCoverageStatsPlayers()
                || s.isCoveragePlayers()
                || s.isCoverageTopScorers()
                || s.isCoverageTopAssists()
                || s.isCoverageTopCards()
                || s.isCoverageInjuries()
                || s.isCoveragePredictions()
                || s.isCoverageOdds();
    }

    public void resetDebounce(String key) {
        Optional.ofNullable(key).ifPresent(k -> {
            lastAttempt.remove(k);
            lastSuccessfulSync.remove(k);
        });
    }

    /**
     * Kullanici ziyaretiyle lig icin {@code covered=true} isareti — populer
     * say, gunluk tazelemeye al. Idempotent: zaten covered ise UPDATE atilmaz.
     *
     * <p>Hizli yol: in-memory {@link #knownCoveredLeagues} setiyle ayni instance
     * suresinde tekrar UPDATE yapilmaz. Uygulama yeniden baslayinca set
     * sifirlanir; bir sonraki ziyaretle gercek DB durumu (covered=true) tekrar
     * tespit edilip set'e konur (gerek olmadigi icin UPDATE atilmaz).
     */
    @org.springframework.transaction.annotation.Transactional
    public void markCoveredIfNeeded(League league) {
        if (league == null) return;
        Long leagueId = league.getId();
        if (knownCoveredLeagues.contains(leagueId)) return;
        if (league.isCovered()) {
            knownCoveredLeagues.add(leagueId);
            return;
        }
        league.setCovered(true);
        leagueRepository.save(league);
        knownCoveredLeagues.add(leagueId);
        log.info("Lig otomatik covered isaretlendi (ilk ziyaret): leagueId={} '{}'",
                leagueId, league.getName());
    }
}

package com.scorestv.football.team;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiQuotaTracker;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.CoachTrophyRepository;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TeamSquad;
import com.scorestv.football.domain.TeamSquadRepository;
import com.scorestv.football.domain.TeamStatisticsRepository;
import com.scorestv.football.domain.TransferRepository;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.sync.CoachTrophiesSyncService;
import com.scorestv.football.sync.CoachesSyncService;
import com.scorestv.football.sync.FixtureSyncService;
import com.scorestv.football.sync.PlayerSeasonStatsSyncService;
import com.scorestv.football.sync.ReferenceSyncService;
import com.scorestv.football.sync.SidelinedSyncService;
import com.scorestv.football.sync.SquadSyncService;
import com.scorestv.football.sync.StandingsSyncService;
import com.scorestv.football.sync.TeamStatisticsSyncService;
import com.scorestv.football.sync.TeamSyncService;
import com.scorestv.football.sync.TransfersSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Takim detay endpoint'i icin ihtiyac aninda sync orkestratoru.
 *
 * <p>Davranis:
 * <ul>
 *   <li>Takim DB'de yoksa {@code /teams?id=X} ile cek</li>
 *   <li>Mevcut sezon icin: squad + transfers
 *       + statistics (oynanan tum ligler) + standings (oynanan tum ligler)
 *       + fikstur</li>
 *   <li>Eski sezon istenirse: statistics + standings + fixtures'i sync et</li>
 *   <li>Kadrodaki tum oyuncular icin sidelined sync (async)</li>
 *   <li>Empty-debounce + freshness pencereleri (squad 24sa, transfers 24sa,
 *       statistics 6sa, standings 1sa, fixtures 6sa, sidelined 6sa)</li>
 * </ul>
 *
 * <p>Transactional DEGIL — alttaki sync servisleri kendi tx'lerini acar.
 */
@Service
public class TeamDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(TeamDetailLazySync.class);

    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);

    /** Aynı takım bu pencerede tetiklendiyse yeniden backfill açılmaz (bot guard). */
    private static final Duration RECENT_ENSURE_GUARD = Duration.ofSeconds(30);
    /** Aynı anda kaç YENİ takım için SENKRON team-info çekimine izin var
     *  (bot request-thread yığılmasını keser). */
    private static final int SYNC_TEAM_PERMITS = 4;

    private static final Duration FRESH_TEAM_INFO = Duration.ofHours(24);
    private static final Duration FRESH_SQUAD = Duration.ofHours(24);
    private static final Duration FRESH_TRANSFERS = Duration.ofHours(12);
    private static final Duration FRESH_STATS_CURRENT = Duration.ofHours(6);
    private static final Duration FRESH_STATS_OLD = Duration.ofHours(72);
    private static final Duration FRESH_STANDINGS = Duration.ofHours(1);
    private static final Duration FRESH_FIXTURES_CURRENT = Duration.ofHours(2);
    private static final Duration FRESH_FIXTURES_OLD = Duration.ofHours(24);
    private static final Duration FRESH_SIDELINED = Duration.ofHours(6);
    private static final Duration FRESH_PLAYER_STATS_CURRENT = Duration.ofHours(12);
    private static final Duration FRESH_PLAYER_STATS_OLD = Duration.ofHours(168);  // 7 gun
    private static final Duration FRESH_COACH = Duration.ofHours(12);
    private static final Duration FRESH_COACH_TROPHIES = Duration.ofHours(24);

    private final TeamRepository teamRepository;
    private final SeasonRepository seasonRepository;
    private final LeagueRepository leagueRepository;
    private final TeamSquadRepository squadRepository;
    private final TransferRepository transferRepository;
    private final TeamStatisticsRepository statsRepository;
    private final StandingRepository standingRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerSidelinedRepository sidelinedRepository;
    private final PlayerSeasonStatRepository playerStatRepository;
    private final CoachRepository coachRepository;
    private final CoachTrophyRepository coachTrophyRepository;

    private final TeamSyncService teamSyncService;
    private final SquadSyncService squadSyncService;
    private final TransfersSyncService transfersSyncService;
    private final TeamStatisticsSyncService statsSyncService;
    private final StandingsSyncService standingsSyncService;
    private final FixtureSyncService fixtureSyncService;
    private final SidelinedSyncService sidelinedSyncService;
    private final PlayerSeasonStatsSyncService playerStatsSyncService;
    private final CoachesSyncService coachesSyncService;
    private final CoachTrophiesSyncService coachTrophiesSyncService;
    private final ReferenceSyncService referenceSyncService;
    private final CacheManager cacheManager;

    /**
     * Takim → discovered current sezon cache. {@code /leagues?team=X} cagrisi
     * pahali (1 request) — sezon ayni instance suresinde DB'den yeniden
     * sorgulanmasin diye tutulur. ensureFor her cagirildiginda once buradan
     * bakar; yoksa DB'den fixture sezonlarina dusup oradan da bulamazsa
     * /leagues?team=X cagirir.
     */
    private final java.util.Map<Long, Integer> discoveredCurrentSeason =
            new ConcurrentHashMap<>();

    /**
     * Takim → kesfedilen TUM sezon yillari (yeni → eski). UI sezon dropdown'u
     * icin kullanilir; kullanici hic ziyaret etmedigi eski sezonlari da
     * gorur ve secebilir (sectiginde lazy sync devreye girer).
     */
    private final java.util.Map<Long, List<Integer>> discoveredSeasons =
            new ConcurrentHashMap<>();

    /** Self proxy — async metodlar icin Spring AOP bypass'i engellemek icin. */
    private final TeamDetailLazySync self;

    private final java.util.Map<String, Instant> lastSuccessfulSync = new ConcurrentHashMap<>();
    private final java.util.Map<String, Instant> lastAttempt = new ConcurrentHashMap<>();

    /**
     * Bu instance suresinde "zaten covered" oldugu gozlemlenen takim id'leri.
     * Aynacak takim cogu zaman covered=true'ya gecisten sonra her ziyarette
     * tekrar UPDATE'lenmemeli; bu set hizli yol saglar (uygulama yeniden
     * baslayinca sifirlanir ve gercek DB durumu tekrar dogrulanir).
     */
    private final java.util.Set<Long> knownCoveredTeams =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 429 cooldown durumu — cooldown aktifken hiç backfill başlatma. */
    private final ApiQuotaTracker quotaTracker;
    /** Takım-bazlı son tetik zamanı — {@link #RECENT_ENSURE_GUARD} için. */
    private final java.util.Map<Long, Instant> recentlyEnsured = new ConcurrentHashMap<>();
    /** Eşzamanlı SENKRON team-info çekimi tavanı (bot request-thread yığılmasını keser). */
    private final Semaphore syncTeamGate = new Semaphore(SYNC_TEAM_PERMITS);

    /**
     * Ağır backfill (squad/stats/standings/fixtures/coach/transfer/...) için ÖZEL
     * sınırlı havuz. Eskiden request thread'i (senkron ensureFor) veya stv-async
     * kullanılıyordu; bot seli altında bunlar dolup 504'e yol açıyordu. İzole;
     * kuyruk dolunca {@link ThreadPoolExecutor.DiscardPolicy} ile fazlası düşürülür
     * (best-effort; DailyTeamRefreshJob + sonraki ziyaret toparlar). Daemon.
     */
    private final ExecutorService lazyExecutor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "stv-team-lazy-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardPolicy());

    @PreDestroy
    void shutdownLazyExecutor() {
        lazyExecutor.shutdown();
    }

    public TeamDetailLazySync(TeamRepository teamRepository,
                              SeasonRepository seasonRepository,
                              LeagueRepository leagueRepository,
                              TeamSquadRepository squadRepository,
                              TransferRepository transferRepository,
                              TeamStatisticsRepository statsRepository,
                              StandingRepository standingRepository,
                              FixtureRepository fixtureRepository,
                              PlayerSidelinedRepository sidelinedRepository,
                              PlayerSeasonStatRepository playerStatRepository,
                              CoachRepository coachRepository,
                              CoachTrophyRepository coachTrophyRepository,
                              TeamSyncService teamSyncService,
                              SquadSyncService squadSyncService,
                              TransfersSyncService transfersSyncService,
                              TeamStatisticsSyncService statsSyncService,
                              StandingsSyncService standingsSyncService,
                              FixtureSyncService fixtureSyncService,
                              SidelinedSyncService sidelinedSyncService,
                              PlayerSeasonStatsSyncService playerStatsSyncService,
                              CoachesSyncService coachesSyncService,
                              CoachTrophiesSyncService coachTrophiesSyncService,
                              ReferenceSyncService referenceSyncService,
                              CacheManager cacheManager,
                              ApiQuotaTracker quotaTracker,
                              @Lazy TeamDetailLazySync self) {
        this.teamRepository = teamRepository;
        this.seasonRepository = seasonRepository;
        this.leagueRepository = leagueRepository;
        this.squadRepository = squadRepository;
        this.transferRepository = transferRepository;
        this.statsRepository = statsRepository;
        this.standingRepository = standingRepository;
        this.fixtureRepository = fixtureRepository;
        this.sidelinedRepository = sidelinedRepository;
        this.playerStatRepository = playerStatRepository;
        this.coachRepository = coachRepository;
        this.coachTrophyRepository = coachTrophyRepository;
        this.teamSyncService = teamSyncService;
        this.squadSyncService = squadSyncService;
        this.transfersSyncService = transfersSyncService;
        this.statsSyncService = statsSyncService;
        this.standingsSyncService = standingsSyncService;
        this.fixtureSyncService = fixtureSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.coachesSyncService = coachesSyncService;
        this.coachTrophiesSyncService = coachTrophiesSyncService;
        this.referenceSyncService = referenceSyncService;
        this.cacheManager = cacheManager;
        this.quotaTracker = quotaTracker;
        this.self = self;
    }

    /**
     * Verilen takim + (opsiyonel) secili sezon icin eksik/bayatlamis modulleri
     * sync eder. Squad/transfers sezondan bagimsiz; statistics/standings/
     * fixtures sezon bazli.
     *
     * @param teamId          takim id
     * @param requestedSeason istenen sezon (null → current sezon)
     * @param currentSeason   takimin (lig'in) current sezonu — secim icin
     */
    public void ensureFor(Long teamId, Integer requestedSeason, Integer currentSeason) {
        // 1) Team info — DB'de yoksa initial fill.
        runIfNeeded("team-info:" + teamId, FRESH_TEAM_INFO,
                () -> !teamRepository.existsById(teamId),
                () -> teamSyncService.syncOne(teamId));

        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) {
            log.warn("Takim sync sonrasi DB'de yine yok: teamId={}", teamId);
            return;
        }

        // Otomatik covered isaretleme — kullanici ziyaret etti, populer say.
        // Sonraki ziyaretlerde DailyTeamRefreshJob bu takimi otomatik tazeler.
        markCoveredIfNeeded(team);

        // COLD START fix: currentSeason null geldiyse (takimda hic fixture yok,
        // ilk ziyaret), /leagues?team=X cagrisi ile takimin oynadigi tum
        // ligler + sezonlar discover edilir. Geri donulen "current" sezon ile
        // sezon-bazli senkronlar (fixtures/standings/statistics) tetiklenebilir.
        if (currentSeason == null) {
            currentSeason = discoverCurrentSeason(teamId);
        }

        Integer effectiveSeason = requestedSeason != null ? requestedSeason : currentSeason;
        boolean isCurrentSeason = currentSeason != null && currentSeason.equals(effectiveSeason);

        // 2-5) Top-level moduller paralel olarak baslat — squad, coach,
        // sezon-modulleri (stats/standings/fixtures/player_stats), transfers.
        // CompletableFuture.allOf ile bekleriz; her gorev DB freshness'i
        // kontrol edip cogu zaman zaten gec gec API'ye gider, hizli skip eder.
        // Paralel kazanim: 5 ligli takim icin 4-5sn → ~1.5sn.
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Squad — sezondan bagimsiz, mevcut kadro (current squad).
        // /players/squads?team=X her zaman GUNCEL kadroyu doner; bunu effective
        // season ile yazariz. Squad senkronu effectiveSeason gecer; current
        // degilse atla (eski sezon kadrosu zaten arsivde olmali).
        if (isCurrentSeason && effectiveSeason != null) {
            futures.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("squad:" + teamId + "-" + effectiveSeason, FRESH_SQUAD,
                            () -> squadRepository.findByTeamIdAndSeason(teamId, effectiveSeason).isEmpty(),
                            () -> squadSyncService.sync(teamId, effectiveSeason))));
        }

        // Transfers — sezondan bagimsiz. Cold start (DB bos) ise inline beklet
        // (yoksa hep bos doner). Dolu ise async tazele — kullanici stale ama
        // dolu veri gorur.
        boolean transfersEmpty = transferRepository.countByTeam(teamId) == 0;
        if (transfersEmpty) {
            futures.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("transfers:" + teamId, FRESH_TRANSFERS,
                            () -> true,
                            () -> transfersSyncService.syncByTeam(teamId))));
        } else {
            // Fire-and-forget — yanit bekletmiyoruz.
            self.refreshTransfersAsync(teamId);
        }

        // Coach + trophies — sezondan bagimsiz, paralel.
        futures.add(CompletableFuture.runAsync(() -> {
            runIfNeeded("coach:" + teamId, FRESH_COACH,
                    () -> coachRepository.findByCurrentTeamId(teamId).isEmpty(),
                    () -> {
                        Long coachId = coachesSyncService.syncByTeam(teamId);
                        if (coachId != null) {
                            runIfNeeded("coach-trophies:" + coachId, FRESH_COACH_TROPHIES,
                                    () -> coachTrophyRepository
                                            .findByCoachIdOrderBySeason(coachId).isEmpty(),
                                    () -> coachTrophiesSyncService.sync(coachId));
                        }
                    });
            // Coach DB'de zaten varsa trophies'i freshness ile ayrica tazele.
            coachRepository.findByCurrentTeamId(teamId).ifPresent(coach ->
                    runIfNeeded("coach-trophies:" + coach.getId(), FRESH_COACH_TROPHIES,
                            () -> coachTrophyRepository
                                    .findByCoachIdOrderBySeason(coach.getId()).isEmpty(),
                            () -> coachTrophiesSyncService.sync(coach.getId())));
        }));

        // Sezon bazli: statistics + standings + fixtures + player_stats.
        // Bu kendi icinde de ligler arasi paralel calisir.
        if (effectiveSeason != null) {
            final Integer fxSeason = effectiveSeason;
            futures.add(CompletableFuture.runAsync(() ->
                    ensureForSeason(teamId, fxSeason, isCurrentSeason)));
        }

        // Tum top-level paralel gorevler tamamlansin.
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (RuntimeException ex) {
            log.warn("Team paralel sync hatasi (teamId={}): {}", teamId, ex.getMessage());
        }

        // 6) Sidelined — async, kadrodaki tum oyuncular icin (squad sync sonrasi).
        if (isCurrentSeason && effectiveSeason != null) {
            self.ensureSidelinedAsync(teamId, effectiveSeason);
        }
    }

    /**
     * Fire-and-forget async versiyon — silent retry zinciri icin. {@code
     * TeamDetailService.refresh()} bunu cagirir; kullanici bekletmez, arka
     * planda tamamlanir.
     */
    public void ensureForAsync(Long teamId, Integer requestedSeason, Integer currentSeason) {
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return; // cooldown → tazeleme atla (DB'de zaten veri var)
        }
        final Instant now = Instant.now();
        final Instant last = recentlyEnsured.get(teamId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return; // yakında zaten tazeledik (bot aynı sayfayı döngüde tarar)
        }
        if (recentlyEnsured.size() > 20_000) recentlyEnsured.clear();
        recentlyEnsured.put(teamId, now);
        // ÖZEL sınırlı havuz (stv-async/request thread değil); dolunca DiscardPolicy.
        lazyExecutor.execute(() -> {
            try {
                ensureFor(teamId, requestedSeason, currentSeason);
            } catch (RuntimeException ex) {
                log.warn("Async takim ensure hatasi: teamId={} season={} — {}",
                        teamId, requestedSeason, ex.getMessage());
            }
        });
    }

    /**
     * YENİ takım (DB'de HİÇ yok) akışı — {@link TeamDetailService#getById}
     * çağırır. Bot/crawler yeni ID'leri gezerken request thread'lerini
     * doldurmasın diye: 429 cooldown'da atla; aynı takım yakında denendiyse atla;
     * {@link #syncTeamGate} dolu ise SENKRON atla (hızlı thin/404). İzin alınırsa
     * YALNIZ team-info (1 API çağrısı) senkron çekilir (sayfa boş gitmesin); ağır
     * modüller (squad/stats/standings/fixtures/...) ARKA PLANDA sınırlı havuzda.
     * Sezon keşfi ({@code /leagues?team=X}) de arka planda — request thread'de değil.
     */
    public void ensureNewTeam(Long teamId, Integer requestedSeason) {
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return;
        }
        final Instant now = Instant.now();
        final Instant last = recentlyEnsured.get(teamId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return;
        }
        if (!syncTeamGate.tryAcquire()) {
            return; // eşzamanlı senkron tavan dolu → request thread'i kilitleme
        }
        try {
            recentlyEnsured.put(teamId, now);
            if (recentlyEnsured.size() > 20_000) recentlyEnsured.clear();
            // Team info (1 çağrı) SENKRON — sayfa boş gitmesin.
            runIfNeeded("team-info:" + teamId, FRESH_TEAM_INFO,
                    () -> !teamRepository.existsById(teamId),
                    () -> teamSyncService.syncOne(teamId));
            teamRepository.findById(teamId).ifPresent(this::markCoveredIfNeeded);
        } finally {
            syncTeamGate.release();
        }
        // Gerisini ARKA PLANDA (sezon keşfi dahil; sel altında DiscardPolicy düşer).
        lazyExecutor.execute(() -> {
            try {
                ensureFor(teamId, requestedSeason, null);
            } catch (RuntimeException ex) {
                log.warn("Team rest arka plan hata teamId={}: {}", teamId, ex.getMessage());
            }
        });
    }

    /** Sezon bazli alt modul senkron: stats + standings + fixtures. */
    private void ensureForSeason(Long teamId, Integer season, boolean isCurrentSeason) {
        // Hangi liglerde oynamis? DB'de yoksa once /leagues?team=X ile lig
        // listesini discover et + uygun lig icin fixtures'i de cek.
        List<Long> leagueIds = fixtureRepository.findLeagueIdsByTeamAndSeason(teamId, season);
        if (leagueIds.isEmpty()) {
            // Cold start: /leagues?team=X cagirip lig listesini al + uygun
            // lig+sezon icin fikstur sync — bu sayede sonraki ensureFor cagrilari
            // findLeagueIdsByTeamAndSeason'dan zaten dolu liste alir.
            leagueIds = bootstrapLeaguesForTeam(teamId, season);
            if (leagueIds.isEmpty()) {
                log.debug("Takim icin lig discover edilemedi: teamId={} season={}",
                        teamId, season);
                return;
            }
        }

        // Her lig icin standings + stats + fixtures paralel, ayrica liglerden
        // bagimsiz player_stats paralel. CompletableFuture.allOf ile beklenir.
        // Tipik 5 ligli takim icin: 15-20 sn → 2-3 sn (ApiFootballClient
        // semaphore'una kadar paralel — daha fazlasi otomatik queue olur).
        Duration statsFresh = isCurrentSeason ? FRESH_STATS_CURRENT : FRESH_STATS_OLD;
        Duration fxFresh = isCurrentSeason ? FRESH_FIXTURES_CURRENT : FRESH_FIXTURES_OLD;

        List<CompletableFuture<Void>> leagueFutures = new ArrayList<>();

        for (Long leagueId : leagueIds) {
            // Standings — paralel: coverage kontrolu + lazy sync
            leagueFutures.add(CompletableFuture.runAsync(() -> {
                Season seasonEntity =
                        seasonRepository.findByLeagueIdAndYear(leagueId, season).orElse(null);
                if (seasonEntity != null && seasonEntity.isCoverageStandings()) {
                    runIfNeeded("standings:" + leagueId + "-" + season, FRESH_STANDINGS,
                            () -> standingRepository
                                    .findByLeagueIdAndSeasonOrderByRankAsc(leagueId, season).isEmpty(),
                            () -> standingsSyncService.sync(leagueId, season));
                }
            }));

            // Statistics — paralel
            leagueFutures.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("team-stats:" + teamId + "-" + leagueId + "-" + season, statsFresh,
                            () -> statsRepository
                                    .findByTeamIdAndLeagueIdAndSeason(teamId, leagueId, season).isEmpty(),
                            () -> statsSyncService.sync(teamId, leagueId, season))));

            // Fixtures — paralel
            leagueFutures.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("fixtures:" + leagueId + "-" + season, fxFresh,
                            () -> fixtureRepository.countByLeagueIdAndSeason(leagueId, season) == 0,
                            () -> fixtureSyncService.syncLeagueSeason(leagueId, season))));
        }

        // Oyuncu sezonluk istatistikleri — takim+sezon bazli, liglerden bagimsiz.
        // Ayri paralel gorev olarak ekle (current 12sa, eski 7gun).
        Duration playerStatsFresh = isCurrentSeason
                ? FRESH_PLAYER_STATS_CURRENT
                : FRESH_PLAYER_STATS_OLD;
        leagueFutures.add(CompletableFuture.runAsync(() ->
                runIfNeeded("player-stats:" + teamId + "-" + season, playerStatsFresh,
                        () -> playerStatRepository.countByTeamIdAndSeason(teamId, season) == 0,
                        () -> playerStatsSyncService.sync(teamId, season))));

        try {
            CompletableFuture.allOf(leagueFutures.toArray(new CompletableFuture[0])).join();
        } catch (RuntimeException ex) {
            log.warn("Team sezon paralel sync hatasi (teamId={} season={}): {}",
                    teamId, season, ex.getMessage());
        }
    }

    /**
     * Transfers tazelemesini background thread'de yapar — istek thread'ini
     * bekletmez. DB'de zaten transfer varsa kullanici cogu kez bayat ama
     * dolu liste gorur; bu sync sonraki istek icin tazeler. Self proxy ile
     * cagrilmali (Spring AOP).
     *
     * <p>Freshness window'a uyar — kisa surede arka arkaya istek gelirse
     * sadece bir kez sync atilir.
     */
    @Async
    public void refreshTransfersAsync(Long teamId) {
        try {
            long before = transferRepository.countByTeam(teamId);
            runIfNeeded("transfers:" + teamId, FRESH_TRANSFERS,
                    () -> transferRepository.countByTeam(teamId) == 0,
                    () -> transfersSyncService.syncByTeam(teamId));
            long after = transferRepository.countByTeam(teamId);
            // Sync sonrasi DB degisti mi? Degistiyse Redis cache'inde takim
            // entry'lerini evict et — bir sonraki istek 15sn TTL beklemeden
            // dogrudan DB'deki yeni veriyi doner.
            if (after != before) {
                evictTeamCache(teamId);
                log.info("Transfers async refresh: teamId={} {} → {} satir, cache evict",
                        teamId, before, after);
            }
        } catch (RuntimeException ex) {
            log.warn("Transfers async refresh hatasi (teamId={}): {}",
                    teamId, ex.getMessage());
        }
    }

    /**
     * Belirli bir takim icin LIVE cache'inde tutulan tum response entry'lerini
     * temizler. Cache key formati: {@code team-{id}-{season}-{lang}}. Tum
     * (sezon, dil) kombinasyonlarini tek seferde temizlemek icin GenericRedis
     * cache'inde {@code clear()} yerine prefix-based evict alternatifi yoktur;
     * basit yaklasim: cache'in tum entry'lerini siler (LIVE cache'inde diger
     * sayfa cache'leri de var — onlar 15sn TTL ile zaten kisa zamanda yenilenir).
     *
     * <p>Daha hassas eviction icin (yalniz bu takimin entry'leri), Redis raw
     * SCAN + DEL ile {@code team-{id}-*} pattern'i kullanilabilir — simdilik
     * tum cache'i temizlemek yeterli (LIVE cache zaten kisa TTL'li).
     */
    private void evictTeamCache(Long teamId) {
        try {
            Cache cache = cacheManager.getCache(FootballCacheNames.LIVE);
            if (cache == null) return;
            // Bilinen (sezon, dil) kombinasyonlarini evict et.
            // ?season= verilmemis (varsayilan): cur. Verilen sezonlar dinamik —
            // discoveredSeasons cache'inden gel.
            for (String lang : new String[] {"tr", "en"}) {
                cache.evict("team-" + teamId + "-cur-" + lang);
            }
            List<Integer> seasons = discoveredSeasons.getOrDefault(teamId, List.of());
            for (Integer season : seasons) {
                for (String lang : new String[] {"tr", "en"}) {
                    cache.evict("team-" + teamId + "-" + season + "-" + lang);
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Cache evict hatasi (teamId={}): {}", teamId, ex.getMessage());
        }
    }

    /**
     * Kadrodaki oyuncularin sidelined kayitlarini async olarak sync eder.
     * Request thread'i beklemez — UI cogu zaman cached sonucu gosterir, bir
     * sonraki erisimde guncel olur.
     */
    @Async
    public void ensureSidelinedAsync(Long teamId, Integer season) {
        try {
            String key = "sidelined-team:" + teamId + "-" + season;
            Instant now = Instant.now();
            Instant lastSuccess = lastSuccessfulSync.get(key);
            if (lastSuccess != null && lastSuccess.isAfter(now.minus(FRESH_SIDELINED))) {
                return;
            }
            Instant attempt = lastAttempt.get(key);
            if (attempt != null && attempt.isAfter(now.minus(EMPTY_RETRY))) {
                return;  // Yakin zamanda denenmis, debounce
            }
            lastAttempt.put(key, now);

            List<TeamSquad> squad = squadRepository.findByTeamIdAndSeason(teamId, season);
            if (squad.isEmpty()) {
                return;
            }
            Set<Long> playerIds = new HashSet<>();
            for (TeamSquad s : squad) {
                if (s.getPlayerId() != null) playerIds.add(s.getPlayerId());
            }
            if (playerIds.isEmpty()) return;
            sidelinedSyncService.syncForPlayers(playerIds);
            lastSuccessfulSync.put(key, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Sidelined async sync hatasi (teamId={}): {}", teamId, ex.getMessage());
        }
    }

    /**
     * runIfNeeded — module-spesifik sync mantigi (Match/League lazy sync ile ayni).
     */
    private void runIfNeeded(String key, Duration freshness,
                             BooleanSupplier dbIsEmpty, Runnable syncCall) {
        try {
            boolean empty = dbIsEmpty.getAsBoolean();
            Instant now = Instant.now();

            if (empty) {
                Instant attempt = lastAttempt.get(key);
                if (attempt != null && attempt.isAfter(now.minus(EMPTY_RETRY))) {
                    return;
                }
            } else if (freshness != null) {
                Instant lastSuccess = lastSuccessfulSync.get(key);
                if (lastSuccess != null && lastSuccess.isAfter(now.minus(freshness))) {
                    return;
                }
            } else {
                return;
            }

            lastAttempt.put(key, now);
            syncCall.run();
            lastSuccessfulSync.put(key, Instant.now());
        } catch (ApiException ex) {
            log.warn("Team lazy sync API hatasi ({}): {}", key, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Team lazy sync beklenmedik hata ({}): {}", key, ex.getMessage());
        }
    }

    /**
     * Cold start: takim DB'de var ama hangi sezonlarda oynadigi belirsizse
     * (fixture yok), {@code /leagues?team=X} cagirip current sezonu bul.
     *
     * <p>Sonuc instance icinde cache'lenir; ayni request lifecycle'inda tekrar
     * cagrilirsa cache'ten doner. Empty-debounce de uygulanir — API surekli
     * bos doner ise her ziyarette tekrar denemiyoruz.
     */
    /**
     * Public: takimin GERCEK current sezonu. Once cache; yoksa /leagues?team=X
     * cagrisi yapip cache'ler. {@link TeamDetailService#getById} bunu cagirip
     * {@link #ensureFor}'a "currentSeason" olarak gecirir — kullanicinin
     * sectigi sezonla (requestedSeason) karistirmamak icin.
     */
    public Integer getOrDiscoverCurrentSeason(Long teamId) {
        return discoverCurrentSeason(teamId);
    }

    private Integer discoverCurrentSeason(Long teamId) {
        Integer cached = discoveredCurrentSeason.get(teamId);
        if (cached != null) return cached;
        String key = "team-leagues:" + teamId;
        Instant attempt = lastAttempt.get(key);
        Instant now = Instant.now();
        if (attempt != null && attempt.isAfter(now.minus(EMPTY_RETRY))) {
            return null;  // yakin zamanda denenmis ve bos donmus
        }
        lastAttempt.put(key, now);
        try {
            ReferenceSyncService.TeamLeaguesResult result =
                    referenceSyncService.syncByTeam(teamId);
            Integer current = result.currentSeason();
            if (current != null) {
                discoveredCurrentSeason.put(teamId, current);
                lastSuccessfulSync.put(key, Instant.now());
            }
            // Tum sezonlari da cache'le — UI dropdown'a fixtures sync'i
            // beklemeden butun yillari verir.
            if (result.allSeasons() != null && !result.allSeasons().isEmpty()) {
                discoveredSeasons.put(teamId, result.allSeasons());
            }
            return current;
        } catch (ApiException ex) {
            log.warn("/leagues?team={} discover API hatasi: {}", teamId, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("/leagues?team={} discover beklenmedik hata: {}", teamId, ex.getMessage());
        }
        return null;
    }

    /**
     * Cold start: takimin verilen sezonda oynadigi ligler DB'de henuz yok.
     * {@code /leagues?team=X} cagirip ligleri kesfeder; sonra her bir lig icin
     * fixture sync tetikler (yalniz verilen sezon).
     *
     * <p>Donen: sezonda lig bulunabilenlerin id'leri (fixture sync sonrasi
     * findLeagueIdsByTeamAndSeason zaten bu liste ile dolu olacak).
     */
    private List<Long> bootstrapLeaguesForTeam(Long teamId, Integer season) {
        ReferenceSyncService.TeamLeaguesResult result;
        try {
            result = referenceSyncService.syncByTeam(teamId);
        } catch (RuntimeException ex) {
            log.warn("/leagues?team={} bootstrap hatasi: {}", teamId, ex.getMessage());
            return List.of();
        }
        if (result.leagueIds().isEmpty()) {
            return List.of();
        }
        // Tum sezonlari da cache'le (UI dropdown icin).
        if (result.allSeasons() != null && !result.allSeasons().isEmpty()) {
            discoveredSeasons.put(teamId, result.allSeasons());
        }
        List<Long> bootstrapped = new java.util.ArrayList<>();
        for (Long leagueId : result.leagueIds()) {
            // Sezon mevcut mu? Sezon kaydi /leagues?team=X tarafindan upsert
            // edildi; ancak takim bu lig'in BU sezonunda oynamadiysa fixture
            // call bos doner — bu yine de denenmeli (cevap bos olabilir).
            try {
                fixtureSyncService.syncLeagueSeason(leagueId, season);
                bootstrapped.add(leagueId);
            } catch (RuntimeException ex) {
                log.warn("Bootstrap fixture sync hatasi: leagueId={} season={} — {}",
                        leagueId, season, ex.getMessage());
            }
        }
        return bootstrapped;
    }

    /**
     * Kullanici ziyaretiyle takim icin {@code covered=true} isareti — populer
     * say, gunluk tazelemeye al. Idempotent: zaten covered ise UPDATE atilmaz.
     *
     * <p>Hizli yol: in-memory {@link #knownCoveredTeams} setiyle ayni instance
     * suresinde tekrar UPDATE yapilmaz. Uygulama yeniden baslayinca set
     * sifirlanir; bir sonraki ziyaretle gercek DB durumu (covered=true) tekrar
     * tespit edilip set'e konur (gerek olmadigi icin UPDATE atilmaz).
     */
    @org.springframework.transaction.annotation.Transactional
    public void markCoveredIfNeeded(Team team) {
        if (team == null) return;
        Long teamId = team.getId();
        if (knownCoveredTeams.contains(teamId)) return;
        if (team.isCovered()) {
            knownCoveredTeams.add(teamId);
            return;
        }
        team.setCovered(true);
        teamRepository.save(team);
        knownCoveredTeams.add(teamId);
        log.info("Takim otomatik covered isaretlendi (ilk ziyaret): teamId={} '{}'",
                teamId, team.getName());
    }

    /**
     * UI sezon dropdown'u icin: {@code /leagues?team=X} cagrisi sirasinda
     * cache'lenen TUM yillarin listesi (yeni → eski). DB henuz fixtures'i
     * sync etmemis olsa bile kullanici dropdown'da gorur.
     *
     * <p>Bos liste donerse caller {@code FixtureRepository.findSeasonYearsByTeam}
     * sonucuna duser (DB derived) — cold-start oncesi ilk istekte boyle olur.
     */
    public List<Integer> getDiscoveredSeasons(Long teamId) {
        List<Integer> cached = discoveredSeasons.get(teamId);
        return cached != null ? cached : List.of();
    }

    /** Test/admin: debounce sifirlama. */
    public void resetDebounce(Collection<String> keys) {
        if (keys == null) return;
        for (String k : keys) {
            lastAttempt.remove(k);
            lastSuccessfulSync.remove(k);
        }
    }
}

package com.scorestv.football.detail;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiQuotaTracker;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureLineupRepository;
import com.scorestv.football.domain.FixturePlayerStatRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.FixtureStatisticRepository;
import com.scorestv.football.domain.InjuryRepository;
import com.scorestv.football.domain.PredictionRepository;
import com.scorestv.football.domain.StandingRepository;
import com.scorestv.football.live.MatchDataReadyBroadcaster;
import com.scorestv.football.sync.FixtureEventsSyncService;
import com.scorestv.football.sync.FixtureLineupsSyncService;
import com.scorestv.football.sync.FixturePlayerStatsSyncService;
import com.scorestv.football.sync.FixtureStatisticsSyncService;
import com.scorestv.football.sync.H2hSyncService;
import com.scorestv.football.sync.InjuriesSyncService;
import com.scorestv.football.sync.PredictionsSyncService;
import com.scorestv.football.sync.StandingsSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maç detayı endpoint'ine geldiğinde DB'deki yan modülleri (h2h, lineups,
 * injuries, predictions, standings, statistics, playerStats) iki sebeple
 * API-Football'dan çeker:
 *
 * <ol>
 *   <li><b>Initial fill:</b> DB tamamen boşsa.
 *   <li><b>Freshness refresh:</b> DB'de veri var ama belirli pencereden eski
 *       (örn. standings 1 saat, predictions 2 saat). Veri güncel kalır.
 * </ol>
 *
 * <p><b>Empty-debounce:</b> API boş yanıt verdiğinde (alt lig için lineups
 * yok vb.) 10 dk içinde tekrar denenmez — quota israfı önlemi.
 *
 * <p><b>Cold start güvenliği:</b> Restart sonrası {@code lastSuccessfulSync}
 * boştur. DB'de zaten veri varsa tazeleme tetiklenmez (thundering herd
 * engeli); sadece DB boş veya zaten denedik-üstünden-pencere-geçti durumunda
 * çağrı yapılır.
 *
 * <p><b>Önemli:</b> Bu bean'in metodu transactional DEĞİL — çağrılan sync
 * servisleri kendi REQUIRED tx'lerini açar. Üstte readOnly tx olsaydı yazma
 * engellenirdi.
 */
@Service
public class MatchDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(MatchDetailLazySync.class);

    /** Empty-debounce: API boş yanıt verirse bu pencerede tekrar denenmez. */
    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);

    /** Async (fire-and-forget) yol için maç-bazlı tetik koruması: aynı maç bu
     *  pencerede zaten tetiklendiyse yeniden fan-out açılmaz. Bot/crawler aynı
     *  URL'leri döngüde tarayınca commonPool/rate-limiter selini keser. Force-
     *  refresh senkron yoldan gelir → bu korumadan ETKİLENMEZ. */
    private static final Duration RECENT_ENSURE_GUARD = Duration.ofSeconds(15);

    /** Lineups deneme penceresi: kickoff'a ≤ 3 sa kala veya başlamış maç. */
    private static final Duration LINEUPS_WINDOW_BEFORE_KICKOFF = Duration.ofHours(3);

    /** Injuries/Predictions deneme penceresi: kickoff'a ≤ 48 sa kala. */
    private static final Duration PREMATCH_WINDOW = Duration.ofHours(48);

    /** Modül başına tazeleme penceresi — bu süre sonunda DB'de veri olsa bile yeniden çekilir. */
    private static final Duration FRESH_H2H = Duration.ofHours(12);
    private static final Duration FRESH_STANDINGS = Duration.ofHours(1);       // API saatlik
    private static final Duration FRESH_PREDICTIONS = Duration.ofHours(2);     // API saatlik, biraz tolerans
    private static final Duration FRESH_INJURIES = Duration.ofHours(4);        // API 4 saatlik
    private static final Duration FRESH_LINEUPS_LIVE = Duration.ofMinutes(30); // canlı maçta subs+last min
    private static final Duration FRESH_STATS_LIVE = Duration.ofMinutes(2);    // canlı statlar — backup; asıl LiveStatisticsJob
    private static final Duration FRESH_PLAYERSTATS_LIVE = Duration.ofMinutes(3);

    /**
     * Maçtan ne kadar sonra tazeleme penceresi kapatılır — sadece initial-fill
     * kalır (DB boşsa). 26 saat = ~2 saat maç süresi + 24 saat post-FT veri
     * düzeltme penceresi. Bu süreden sonra eski maç detayını açan kullanıcı
     * için artık API'ye gidilmez (DB'de ne varsa o gösterilir).
     */
    private static final Duration POST_KICKOFF_REFRESH_WINDOW = Duration.ofHours(26);

    /** "Henüz başlamadı" sayılan durum kodları. */
    private static final Set<String> NOT_STARTED = Set.of("NS", "TBD");

    /** "Şu an oynanıyor" sayılan durum kodları — canlı tazeleme için. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    /**
     * Senkron (force-refresh) yolda fan-out'un tamamlanması için ÜST SINIR.
     *
     * <p>Eskiden 20sn'di; API/rate-limiter tıkandığında (çok canlı maç + çok
     * detay açılışı) request thread'i 20sn bloklanıyor, mobil {@code receiveTimeout}
     * (15sn) dolup "internet yavaş/yok" hatası veriyordu ve detay ekranı sonsuz
     * dönüyordu. Skor zaten fixture entity'de (live ticker) taze; ağır modüller
     * (h2h/lineup/stats/events) bitince WebSocket "data-ready" push'u ile gelir.
     * Bu yüzden kısa bir cap yeter: response ≤6sn döner (mobil timeout'un çok
     * altında), eksik modüller arkada tamamlanıp push'lanır.
     */
    private static final int FORCE_SYNC_WAIT_SECONDS = 6;

    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository eventRepository;
    private final FixtureLineupRepository lineupRepository;
    private final FixtureStatisticRepository statisticRepository;
    private final FixturePlayerStatRepository playerStatRepository;
    private final InjuryRepository injuryRepository;
    private final PredictionRepository predictionRepository;
    private final StandingRepository standingRepository;

    private final H2hSyncService h2hSyncService;
    private final FixtureEventsSyncService eventsSyncService;
    private final FixtureLineupsSyncService lineupsSyncService;
    private final FixtureStatisticsSyncService statisticsSyncService;
    private final FixturePlayerStatsSyncService playerStatsSyncService;
    private final InjuriesSyncService injuriesSyncService;
    private final PredictionsSyncService predictionsSyncService;
    private final StandingsSyncService standingsSyncService;

    /**
     * Module-ready WebSocket broadcaster — lazy sync ile bir modul DB'ye
     * yazildiginda mobile/web client'lara push gonderir. Client polling
     * yapmadan UI'da gosterir. {@link #runIfNeeded} icinde sync basarili
     * olduktan sonra cagrilir.
     */
    private final MatchDataReadyBroadcaster readyBroadcaster;

    /**
     * Self proxy — {@link #ensureForAsync} Spring AOP advice'i (@Async)
     * ancak proxy uzerinden cagrildiginda calisir; doğrudan {@code this.}
     * self-invocation yapildiginda bypass olur ve cagri SENKRON kalir.
     * {@code @Lazy} sirkuler bagimlilik riskini ortadan kaldirir.
     */
    private final MatchDetailLazySync self;

    /** Son başarılı sync zamanı — tazeleme penceresi için. */
    private final Map<String, Instant> lastSuccessfulSync = new ConcurrentHashMap<>();
    /** Son deneme zamanı — empty-debounce için (API boş döndüğünde). */
    private final Map<String, Instant> lastAttempt = new ConcurrentHashMap<>();
    /** Async yol maç-bazlı tetik zamanı — {@link #RECENT_ENSURE_GUARD} için. */
    private final Map<Long, Instant> recentlyEnsured = new ConcurrentHashMap<>();

    /** 429 cooldown durumu — cooldown aktifken fan-out'u hiç açmamak için. */
    private final ApiQuotaTracker quotaTracker;

    /**
     * Lazy sync fan-out'u için ÖZEL sınırlı havuz. Önceden {@code runAsync}
     * paylaşımlı {@link java.util.concurrent.ForkJoinPool#commonPool()} kullanıyordu;
     * her task ağ + rate-limiter üstünde bloklandığı için bot yükünde commonPool
     * açlığa düşüp uygulamanın geri kalanını da yavaşlatıyordu. Kendi havuzu ile
     * izole; kuyruk dolunca {@link ThreadPoolExecutor.CallerRunsPolicy} çağıran
     * thread'e geri baskı uygular (deadlock yok — dış {@code stv-async} havuzu
     * ayrıdır). Daemon thread'ler.
     */
    private final ExecutorService lazyExecutor = new ThreadPoolExecutor(
            8, 24, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(256),
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "stv-lazysync-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    @PreDestroy
    void shutdownLazyExecutor() {
        lazyExecutor.shutdown();
    }

    /** Fan-out task'ini commonPool yerine özel havuzda çalıştırır. */
    private CompletableFuture<Void> async(Runnable task) {
        return CompletableFuture.runAsync(task, lazyExecutor);
    }

    public MatchDetailLazySync(FixtureRepository fixtureRepository,
                               FixtureEventRepository eventRepository,
                               FixtureLineupRepository lineupRepository,
                               FixtureStatisticRepository statisticRepository,
                               FixturePlayerStatRepository playerStatRepository,
                               InjuryRepository injuryRepository,
                               PredictionRepository predictionRepository,
                               StandingRepository standingRepository,
                               H2hSyncService h2hSyncService,
                               FixtureEventsSyncService eventsSyncService,
                               FixtureLineupsSyncService lineupsSyncService,
                               FixtureStatisticsSyncService statisticsSyncService,
                               FixturePlayerStatsSyncService playerStatsSyncService,
                               InjuriesSyncService injuriesSyncService,
                               PredictionsSyncService predictionsSyncService,
                               StandingsSyncService standingsSyncService,
                               MatchDataReadyBroadcaster readyBroadcaster,
                               ApiQuotaTracker quotaTracker,
                               @Lazy MatchDetailLazySync self) {
        this.fixtureRepository = fixtureRepository;
        this.eventRepository = eventRepository;
        this.lineupRepository = lineupRepository;
        this.statisticRepository = statisticRepository;
        this.playerStatRepository = playerStatRepository;
        this.injuryRepository = injuryRepository;
        this.predictionRepository = predictionRepository;
        this.standingRepository = standingRepository;
        this.h2hSyncService = h2hSyncService;
        this.eventsSyncService = eventsSyncService;
        this.lineupsSyncService = lineupsSyncService;
        this.statisticsSyncService = statisticsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.injuriesSyncService = injuriesSyncService;
        this.predictionsSyncService = predictionsSyncService;
        this.standingsSyncService = standingsSyncService;
        this.readyBroadcaster = readyBroadcaster;
        this.quotaTracker = quotaTracker;
        this.self = self;
    }

    /**
     * Fire-and-forget asenkron versiyonu — request thread'i beklemez.
     *
     * <p>Normal mac detay istegi bunu cagirir: cevap DB'de o anda ne varsa
     * onunla DONER (boş olabilir ilk açılışta), eksik modüller arkada
     * doldurulur. Mobile {@code MatchDetailController} ilk acilis sonrasi
     * "ince" cevap algiladiginda 2-5sn'de silent refetch yapar — kullanici
     * birkac saniye sonra dolu veriyi gorur.
     *
     * <p>Force refresh akisi BU metodu cagirmaz; doğrudan senkron
     * {@link #ensureFor(Long)} cagrir cunku kullanici "Yenile"ye basti ve
     * fresh veri bekliyor.
     *
     * <p>Spring AOP gerekligi: bu metoda self proxy uzerinden cagri
     * ({@link MatchDetailService#getById} icinde {@code self.ensureForAsync})
     * — direkt {@code this.ensureForAsync} cagrilirsa {@code @Async}
     * bypass olur.
     */
    @Async
    public void ensureForAsync(Long fixtureId) {
        // 429 cooldown aktifken alt çağrıların hepsi nasılsa hızlı-hata verir;
        // fan-out açmak sadece commonPool/log spam üretir → hiç başlama.
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return;
        }
        // Aynı maç için kısa süre önce zaten tetiklendiyse tekrar tetikleme —
        // bot/crawler aynı URL'leri döngüde tarar. Force-refresh bu yoldan
        // GEÇMEZ (senkron ensureFor çağrılır) → kullanıcı "Yenile"si etkilenmez.
        Instant now = Instant.now();
        Instant last = recentlyEnsured.get(fixtureId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return;
        }
        if (recentlyEnsured.size() > 20_000) {
            recentlyEnsured.clear();  // sınırsız büyümeyi önle (bot binlerce maç tarar)
        }
        recentlyEnsured.put(fixtureId, now);
        ensureForInternal(fixtureId, true);
    }

    /**
     * Senkron / force-refresh yolu — arşiv-yaş kapısı UYGULANMAZ (kullanıcı
     * açıkça "Yenile"ye bastı, eski maç bile olsa fresh veri istiyor).
     */
    public void ensureFor(Long fixtureId) {
        ensureForInternal(fixtureId, false);
    }

    /**
     * Verilen maç için eksik veya bayatlamış modülleri PARALEL sync eder.
     *
     * <p>Modüller (h2h, lineups, injuries, predictions, standings, statistics,
     * playerStats, events) {@link CompletableFuture}'larla aynı anda
     * başlatılır; {@code allOf().join()} ile hepsinin tamamlanması beklenir.
     * Net süre seri akışın 1/8'i kadar düşer (8 modul × 500ms = 4sn → ~1sn).
     *
     * <p>{@link SyncRateLimiter} her API çağrısını semaphore ile yönetir —
     * paralel çağrılar burst etmez, hız limitine saygı duyar.
     *
     * <p>Hatalar her future içinde yutulur (runIfNeeded warn loglar) — bir
     * modülün hatası diğerlerini etkilemez.
     */
    private void ensureForInternal(Long fixtureId, boolean botPath) {
        Fixture fixture = fixtureRepository.findOneWithDetails(fixtureId).orElse(null);
        if (fixture == null) {
            return;  // Maç DB'de yok → getById nasılsa 404 dönecek.
        }
        Long homeId = fixture.getHomeTeam().getId();
        Long awayId = fixture.getAwayTeam().getId();
        Long leagueId = fixture.getLeague() != null ? fixture.getLeague().getId() : null;
        Integer season = fixture.getLeague() != null
                ? fixture.getLeague().getCurrentSeason() : null;
        Instant kickoff = fixture.getKickoffAt();
        Instant now = Instant.now();
        boolean started = isStarted(fixture);
        boolean live = isLive(fixture);
        boolean upcoming = !started && kickoff != null && kickoff.isAfter(now);
        Duration timeToKickoff = (kickoff != null && upcoming)
                ? Duration.between(now, kickoff)
                : Duration.ZERO;

        boolean refreshAllowed = !started
                || live
                || (kickoff != null
                        && Duration.between(kickoff, now).compareTo(POST_KICKOFF_REFRESH_WINDOW) < 0);
        boolean lineupWindow = started
                || (upcoming && timeToKickoff.compareTo(LINEUPS_WINDOW_BEFORE_KICKOFF) <= 0);
        boolean prematchOrLive = started
                || (upcoming && timeToKickoff.compareTo(PREMATCH_WINDOW) <= 0);

        // Bot/crawler yolu + eski-bitmiş arşiv maçı → lazy fetch HİÇ açma.
        // refreshAllowed=false ise (bitmiş + kickoff POST_KICKOFF_REFRESH_WINDOW'dan
        // eski) zaten yalnız initial-fill kalırdı; bot'un döngüde taradığı binlerce
        // eski maç için bu initial-fetch'ler rate-limit cooldown + 20sn timeout
        // selini yaratıyor. DB'de ne varsa o gösterilir; gerçek kullanıcı
        // pull-to-refresh ile (senkron yol, botPath=false) yine çektirebilir.
        if (botPath && !refreshAllowed) {
            return;
        }

        // Tum tasklari paralel topla
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        // 1) H2H — TÜM karşılaşmalar. 12sa tazeleme.
        tasks.add(async(() ->
                runIfNeeded("h2h:" + fixtureId, freshOrNone(FRESH_H2H, refreshAllowed),
                        () -> {
                            List<Fixture> meetings = fixtureRepository.findMeetings(
                                    homeId, awayId, PageRequest.of(0, 2));
                            return meetings.stream()
                                    .noneMatch(m -> !m.getId().equals(fixtureId));
                        },
                        () -> h2hSyncService.sync(homeId, awayId),
                        fixtureId, "h2h")));

        // 2) Lineups — başlamış veya kickoff'a ≤3 sa kala
        if (lineupWindow) {
            Duration freshness = (live && refreshAllowed) ? FRESH_LINEUPS_LIVE : null;
            tasks.add(async(() ->
                    runIfNeeded("lineups:" + fixtureId, freshness,
                            () -> lineupRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> lineupsSyncService.sync(fixtureId),
                            fixtureId, "lineups")));
        }

        // 3) Injuries — başlamış veya ≤48 sa kala. 4sa tazeleme
        if (prematchOrLive) {
            tasks.add(async(() ->
                    runIfNeeded("injuries:" + fixtureId,
                            freshOrNone(FRESH_INJURIES, refreshAllowed),
                            () -> injuryRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> injuriesSyncService.sync(fixtureId),
                            fixtureId, "injuries")));
        }

        // 4) Predictions — başlamış/canlı/upcoming. 2sa tazeleme
        if (prematchOrLive) {
            tasks.add(async(() ->
                    runIfNeeded("predictions:" + fixtureId,
                            freshOrNone(FRESH_PREDICTIONS, refreshAllowed),
                            () -> predictionRepository.findByFixtureId(fixtureId)
                                    .map(p -> p.getTeamsJson() == null)
                                    .orElse(true),
                            () -> predictionsSyncService.sync(fixtureId),
                            fixtureId, "predictions")));
        }

        // 5) Standings — lig+sezon belliyse. 1sa tazeleme.
        // Standings key leagueId-season ama push fixtureId'ye gider —
        // kullanici mac sayfasinda dinler, lig sayfasi ayri topic'i acabilir.
        if (leagueId != null && season != null) {
            final Long lid = leagueId;
            final Integer s = season;
            tasks.add(async(() ->
                    runIfNeeded("standings:" + lid + "-" + s,
                            freshOrNone(FRESH_STANDINGS, refreshAllowed),
                            () -> standingRepository
                                    .findByLeagueIdAndSeasonOrderByRankAsc(lid, s).isEmpty(),
                            () -> standingsSyncService.sync(lid, s),
                            fixtureId, "standings")));
        }

        // 6) Statistics — başlamış maç. Canlıda 2dk
        if (started) {
            Duration freshness = (live && refreshAllowed) ? FRESH_STATS_LIVE : null;
            tasks.add(async(() ->
                    runIfNeeded("statistics:" + fixtureId, freshness,
                            () -> statisticRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> statisticsSyncService.sync(fixtureId),
                            fixtureId, "stats")));
        }

        // 7) Player stats — başlamış maç. Canlıda 3dk
        if (started) {
            Duration freshness = (live && refreshAllowed) ? FRESH_PLAYERSTATS_LIVE : null;
            tasks.add(async(() ->
                    runIfNeeded("playerStats:" + fixtureId, freshness,
                            () -> playerStatRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> playerStatsSyncService.sync(fixtureId),
                            fixtureId, "playerStats")));
        }

        // 8) Events — başlamış maç. Canlıda 30sn
        if (started) {
            Duration freshness = (live && refreshAllowed) ? Duration.ofSeconds(30) : null;
            tasks.add(async(() ->
                    runIfNeeded("events:" + fixtureId, freshness,
                            () -> eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId).isEmpty(),
                            () -> eventsSyncService.sync(fixtureId),
                            fixtureId, "events")));
        }

        // Async (bot/normal açılış) yolunda BEKLEME: çağıran (@Async stv-async)
        // thread'i hemen serbest bırak — böylece bildirim dispatch'i gibi diğer
        // @Async işleri bu thread'i 20sn beklemez. Tasklar arka planda
        // lazyExecutor'da koşar; her modül bitince WebSocket "data-ready" push'u
        // zaten gider. Yalnız SENKRON (force-refresh) yolda beklenir ki hemen
        // ardından loadCachedResponse taze veriyi okusun.
        if (botPath) {
            return;
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                    .get(FORCE_SYNC_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            // Cap'e takildi: yavaş modüller arkada lazyExecutor'da devam eder ve
            // bitince WebSocket "data-ready" push'u ile gelir. Response yine ≤cap
            // döner (mobil timeout'u dolmaz). Bu artik NORMAL akış — debug seviyesi.
            log.debug("ensureFor {}sn cap'inde tamamlanmadi (modüller arkada devam): fixtureId={}",
                    FORCE_SYNC_WAIT_SECONDS, fixtureId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException ignored) {
            // Tek tek tasklar zaten exception'lari yutuyor; buraya gelmez.
        }
    }

    /**
     * Yardımcı: refresh izni varsa belirtilen tazeleme penceresi, yoksa null
     * ({@link #runIfNeeded} bunu "initial-only" olarak yorumlar).
     */
    private static Duration freshOrNone(Duration base, boolean refreshAllowed) {
        return refreshAllowed ? base : null;
    }

    /**
     * Bir modülü gerek varsa sync eder. Karar mantığı:
     * <ul>
     *   <li>DB boşsa → empty-debounce penceresi (10dk) geçtiyse sync</li>
     *   <li>DB doluysa + {@code freshness} verilmişse → son başarılı sync
     *       o pencereden eskiyse sync</li>
     *   <li>DB doluysa + {@code freshness=null} → sync yok (initial-only)</li>
     * </ul>
     *
     * <p>Cold start (lastSuccessfulSync map'i boş) güvenliği: DB doluysa ve
     * önceki sync timestamp'i yoksa tazeleme TETİKLENMEZ — restart sonrası
     * her detay açılışında thundering herd olmasın.
     *
     * <p><b>WebSocket push:</b> Sync gerçekten çalıştığında (boş debounce'a
     * takılmadıysa, freshness'a uymuyorsa) ve başarılı tamamlandığında
     * {@link MatchDataReadyBroadcaster} ile {@code fixtureId} aboneklerine
     * "data-ready" sinyali gönderilir. Mobile/web client polling yapmadan
     * UI'da gosterir.
     *
     * @param fixtureId  Push hangi maç için gidecek. Standings gibi modüller
     *                   leagueId+season bazlı yazsa da push fixtureId
     *                   üzerinden — kullanıcı maç sayfasındadır, lig sayfası
     *                   ayrı dinler.
     * @param moduleName WebSocket payload'ında geçecek modül adı
     *                   (events|lineups|stats|playerStats|h2h|standings|
     *                   predictions|injuries).
     */
    private void runIfNeeded(String key, Duration freshness,
                             java.util.function.BooleanSupplier dbIsEmpty,
                             Runnable syncCall,
                             Long fixtureId, String moduleName) {
        try {
            boolean empty = dbIsEmpty.getAsBoolean();
            Instant now = Instant.now();

            if (empty) {
                // Boşsa empty-debounce kontrolü.
                Instant attempt = lastAttempt.get(key);
                if (attempt != null && attempt.isAfter(now.minus(EMPTY_RETRY))) {
                    return;  // Yakın zamanda denedik, boş döndü, tekrar deneme.
                }
            } else if (freshness != null) {
                // Doluysa ve tazeleme penceresi varsa kontrol et.
                Instant lastSuccess = lastSuccessfulSync.get(key);
                if (lastSuccess == null) {
                    // Cold start: bu instance'da hiç sync yapmadık, DB veri var
                    // → tazeleme tetikleme (thundering herd engeli). Bir
                    // "stub" timestamp koy ki bir sonraki çağrıda pencere
                    // gerçekten dolsun.
                    lastSuccessfulSync.put(key, now);
                    return;
                }
                if (lastSuccess.isAfter(now.minus(freshness))) {
                    return;  // Hâlâ taze.
                }
            } else {
                // Dolu + freshness yok → initial-only mod, atla.
                return;
            }

            // Sync zamanı.
            lastAttempt.put(key, now);
            syncCall.run();
            // Başarılı tamamlandı (exception fırlatmadı). Tazeleme zamanını damgala.
            lastSuccessfulSync.put(key, Instant.now());

            // Module-ready push → mobile/web client UI'larini polling olmadan
            // anlik tazelesin. Broadcast hatasi sync isini bloklamaz
            // (broadcaster icinde yutulur).
            readyBroadcaster.publish(fixtureId, moduleName);
        } catch (ApiException ex) {
            log.warn("Lazy sync başarısız ({} — API): {}", key, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Lazy sync beklenmedik hata ({}): {}", key, ex.getMessage());
        }
    }

    /** NS/TBD ise henüz başlamadı; aksi halde başladı veya bitti sayılır. */
    private static boolean isStarted(Fixture fixture) {
        String s = fixture.getStatusShort();
        return s != null && !NOT_STARTED.contains(s);
    }

    /** "Şu an oynanıyor" — canlı tazeleme pencereleri için. */
    private static boolean isLive(Fixture fixture) {
        return LIVE_STATUSES.contains(fixture.getStatusShort());
    }

    /** Test/admin amaçlı: belirli bir key için debounce/freshness'i sıfırla. */
    public void resetDebounce(String key) {
        Optional.ofNullable(key).ifPresent(k -> {
            lastAttempt.remove(k);
            lastSuccessfulSync.remove(k);
        });
    }

    /**
     * Verilen maç (ve liginin standings'i) için TÜM debounce + freshness
     * timestamp'lerini siler. Bir sonraki {@link #ensureFor} çağrısı her
     * modülü baştan deneyecek — boş API yanıtı sonrası 10dk debounce'a
     * takılı kalanlar da bypass olur.
     *
     * <p>Mobile pull-to-refresh / TopBar refresh butonundan tetiklenir.
     * Bu yan modüller yan modül; ana maç verisi (fixture entity) zaten DB'de.
     */
    public void resetForFixture(Long fixtureId) {
        if (fixtureId == null) return;
        String suffix = ":" + fixtureId;
        lastAttempt.keySet().removeIf(k -> k.endsWith(suffix));
        lastSuccessfulSync.keySet().removeIf(k -> k.endsWith(suffix));
        // Standings key'i farklı format ("standings:leagueId-season") —
        // fixture entity'sinden lig+sezonu çöz ve onu da temizle.
        Fixture fixture = fixtureRepository.findOneWithDetails(fixtureId).orElse(null);
        if (fixture != null && fixture.getLeague() != null) {
            Integer season = fixture.getLeague().getCurrentSeason();
            if (season != null) {
                String key = "standings:" + fixture.getLeague().getId() + "-" + season;
                lastAttempt.remove(key);
                lastSuccessfulSync.remove(key);
            }
        }
    }
}

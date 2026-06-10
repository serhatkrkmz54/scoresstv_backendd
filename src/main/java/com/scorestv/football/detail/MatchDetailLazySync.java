package com.scorestv.football.detail;

import com.scorestv.common.ApiException;
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
        ensureFor(fixtureId);
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
    public void ensureFor(Long fixtureId) {
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

        // Tum tasklari paralel topla
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        // 1) H2H — TÜM karşılaşmalar. 12sa tazeleme.
        tasks.add(CompletableFuture.runAsync(() ->
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
            tasks.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("lineups:" + fixtureId, freshness,
                            () -> lineupRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> lineupsSyncService.sync(fixtureId),
                            fixtureId, "lineups")));
        }

        // 3) Injuries — başlamış veya ≤48 sa kala. 4sa tazeleme
        if (prematchOrLive) {
            tasks.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("injuries:" + fixtureId,
                            freshOrNone(FRESH_INJURIES, refreshAllowed),
                            () -> injuryRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> injuriesSyncService.sync(fixtureId),
                            fixtureId, "injuries")));
        }

        // 4) Predictions — başlamış/canlı/upcoming. 2sa tazeleme
        if (prematchOrLive) {
            tasks.add(CompletableFuture.runAsync(() ->
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
            tasks.add(CompletableFuture.runAsync(() ->
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
            tasks.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("statistics:" + fixtureId, freshness,
                            () -> statisticRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> statisticsSyncService.sync(fixtureId),
                            fixtureId, "stats")));
        }

        // 7) Player stats — başlamış maç. Canlıda 3dk
        if (started) {
            Duration freshness = (live && refreshAllowed) ? FRESH_PLAYERSTATS_LIVE : null;
            tasks.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("playerStats:" + fixtureId, freshness,
                            () -> playerStatRepository.findByFixtureId(fixtureId).isEmpty(),
                            () -> playerStatsSyncService.sync(fixtureId),
                            fixtureId, "playerStats")));
        }

        // 8) Events — başlamış maç. Canlıda 30sn
        if (started) {
            Duration freshness = (live && refreshAllowed) ? Duration.ofSeconds(30) : null;
            tasks.add(CompletableFuture.runAsync(() ->
                    runIfNeeded("events:" + fixtureId, freshness,
                            () -> eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId).isEmpty(),
                            () -> eventsSyncService.sync(fixtureId),
                            fixtureId, "events")));
        }

        // Hepsinin bitmesini bekle — hatalar tasklar icinde yutuldu
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                    .get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException ex) {
            log.warn("Parallel ensureFor 20sn'de tamamlanmadi: fixtureId={}", fixtureId);
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

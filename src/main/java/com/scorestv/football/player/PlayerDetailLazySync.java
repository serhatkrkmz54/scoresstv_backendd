package com.scorestv.football.player;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiQuotaTracker;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerCareerTeamRepository;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.domain.PlayerTrophyRepository;
import com.scorestv.football.domain.TransferRepository;
import com.scorestv.football.sync.PlayerCareerTeamsSyncService;
import com.scorestv.football.sync.PlayerProfileSyncService;
import com.scorestv.football.sync.PlayerTrophiesSyncService;
import com.scorestv.football.sync.SidelinedSyncService;
import com.scorestv.football.sync.TransfersSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * Oyuncu detay endpoint'i icin ihtiyac aninda sync orkestratoru.
 *
 * <p>Davranis:
 * <ul>
 *   <li>Oyuncu DB'de yoksa /players?id=X&season=current cagrisi ile profil
 *       + current sezon istatistigi cekilir</li>
 *   <li>Kariyer takimlari, kupalar, transferler, sakatlık geçmişi — bayatlamis
 *       olanlar tazelenir</li>
 *   <li>Kullanici sezon dropdown'undan geçmiş sezon secerse o sezon
 *       lazy olarak cekilir</li>
 *   <li>Sync sonrasi cache evict — bir sonraki istek hemen taze veri gorur</li>
 * </ul>
 *
 * <p>Auto-cover: kullanici ziyaret eden oyuncuyu {@code covered=true}
 * isaretler — DailyPlayerRefreshJob bundan sonra gunluk tazelemeye alir.
 */
@Service
public class PlayerDetailLazySync {

    private static final Logger log = LoggerFactory.getLogger(PlayerDetailLazySync.class);

    private static final Duration EMPTY_RETRY = Duration.ofMinutes(10);

    /** Aynı oyuncu bu pencerede zaten tetiklendiyse yeniden backfill açılmaz —
     *  bot/crawler aynı sayfaları döngüde tarayınca API + thread selini keser. */
    private static final Duration RECENT_ENSURE_GUARD = Duration.ofSeconds(30);
    /** Aynı anda kaç YENİ oyuncu için SENKRON profil çekimine izin var. Bot seli
     *  request thread'lerini doldurmasın: tavan dolunca senkron atlanır (thin/404
     *  hızlı döner, arka plan + sonraki ziyaret toparlar) → 504 yığılması biter. */
    private static final int SYNC_PROFILE_PERMITS = 6;

    private static final Duration FRESH_PROFILE = Duration.ofHours(24);
    private static final Duration FRESH_CAREER_TEAMS = Duration.ofHours(24);
    private static final Duration FRESH_TROPHIES = Duration.ofHours(48);
    private static final Duration FRESH_TRANSFERS = Duration.ofHours(12);
    private static final Duration FRESH_SIDELINED = Duration.ofHours(6);
    private static final Duration FRESH_STATS_CURRENT = Duration.ofHours(12);
    private static final Duration FRESH_STATS_OLD = Duration.ofHours(168);  // 7 gun

    private final PlayerRepository playerRepository;
    private final PlayerCareerTeamRepository careerTeamRepository;
    private final PlayerTrophyRepository trophyRepository;
    private final TransferRepository transferRepository;
    private final PlayerSidelinedRepository sidelinedRepository;
    private final PlayerSeasonStatRepository statRepository;

    private final PlayerProfileSyncService profileSyncService;
    private final PlayerCareerTeamsSyncService careerTeamsSyncService;
    private final PlayerTrophiesSyncService trophiesSyncService;
    private final TransfersSyncService transfersSyncService;
    private final SidelinedSyncService sidelinedSyncService;

    private final CacheManager cacheManager;
    private final PlayerDetailLazySync self;

    private final java.util.Map<String, Instant> lastSuccessfulSync = new ConcurrentHashMap<>();
    private final java.util.Map<String, Instant> lastAttempt = new ConcurrentHashMap<>();
    private final java.util.Set<Long> knownCoveredPlayers =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 429 cooldown durumu — cooldown aktifken hiç backfill başlatma. */
    private final ApiQuotaTracker quotaTracker;
    /** Oyuncu-bazlı son tetik zamanı — {@link #RECENT_ENSURE_GUARD} için. */
    private final java.util.Map<Long, Instant> recentlyEnsured = new ConcurrentHashMap<>();
    /** Eşzamanlı SENKRON profil çekimi tavanı (bot request-thread yığılmasını keser). */
    private final Semaphore syncProfileGate = new Semaphore(SYNC_PROFILE_PERMITS);

    /**
     * Ağır backfill (kariyer/kupa/transfer/sakatlık/stat) için ÖZEL sınırlı havuz.
     * Eskiden {@code @Async} (stv-async) veya request thread'i kullanılıyordu; bot
     * seli altında bunlar dolup 504'e yol açıyordu. Bu havuz izole; kuyruk dolunca
     * {@link ThreadPoolExecutor.DiscardPolicy} ile fazlası SESSİZCE düşürülür —
     * zenginleştirme best-effort'tur, DailyPlayerRefreshJob + sonraki ziyaret
     * toparlar. Daemon thread'ler.
     */
    private final ExecutorService lazyExecutor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "stv-player-lazy-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardPolicy());

    @PreDestroy
    void shutdownLazyExecutor() {
        lazyExecutor.shutdown();
    }

    public PlayerDetailLazySync(PlayerRepository playerRepository,
                                PlayerCareerTeamRepository careerTeamRepository,
                                PlayerTrophyRepository trophyRepository,
                                TransferRepository transferRepository,
                                PlayerSidelinedRepository sidelinedRepository,
                                PlayerSeasonStatRepository statRepository,
                                PlayerProfileSyncService profileSyncService,
                                PlayerCareerTeamsSyncService careerTeamsSyncService,
                                PlayerTrophiesSyncService trophiesSyncService,
                                TransfersSyncService transfersSyncService,
                                SidelinedSyncService sidelinedSyncService,
                                CacheManager cacheManager,
                                ApiQuotaTracker quotaTracker,
                                @Lazy PlayerDetailLazySync self) {
        this.playerRepository = playerRepository;
        this.careerTeamRepository = careerTeamRepository;
        this.trophyRepository = trophyRepository;
        this.transferRepository = transferRepository;
        this.sidelinedRepository = sidelinedRepository;
        this.statRepository = statRepository;
        this.profileSyncService = profileSyncService;
        this.careerTeamsSyncService = careerTeamsSyncService;
        this.trophiesSyncService = trophiesSyncService;
        this.transfersSyncService = transfersSyncService;
        this.sidelinedSyncService = sidelinedSyncService;
        this.cacheManager = cacheManager;
        this.quotaTracker = quotaTracker;
        this.self = self;
    }

    /**
     * Verilen oyuncu + (opsiyonel) secili sezon icin eksik/bayatlamis
     * modulleri sync eder.
     *
     * @param playerId       oyuncu id
     * @param requestedSeason istenen sezon (null → current)
     * @param currentSeason  oyuncunun gercek current sezonu (DB'den/profile'dan)
     */
    public void ensureFor(Long playerId, Integer requestedSeason, Integer currentSeason) {
        // 1) Profile + current sezon stats — DB'de yoksa inline sync
        runIfNeeded("player-profile:" + playerId, FRESH_PROFILE,
                () -> !playerRepository.existsById(playerId),
                () -> {
                    if (currentSeason != null) {
                        profileSyncService.sync(playerId, currentSeason);
                    }
                });

        Player player = playerRepository.findById(playerId).orElse(null);
        if (player == null) {
            log.warn("Player sync sonrasi DB'de yine yok: playerId={}", playerId);
            return;
        }

        // 2) Auto-cover
        markCoveredIfNeeded(player);

        // 3-7) Ağır modüller.
        ensureRest(playerId, requestedSeason, currentSeason);
    }

    /**
     * Ağır modüller: kariyer takımları, kupalar, sakatlık, transferler, sezon
     * stats. {@link #ensureFor} (var-olan oyuncu) içinden VE {@link #ensureNewPlayer}
     * (yeni oyuncu) akışında ARKA PLANDA çağrılır — request thread'ini bloklamaz.
     */
    private void ensureRest(Long playerId, Integer requestedSeason, Integer currentSeason) {
        // 3) Career teams
        runIfNeeded("player-career:" + playerId, FRESH_CAREER_TEAMS,
                () -> careerTeamRepository.countByPlayerId(playerId) == 0,
                () -> careerTeamsSyncService.sync(playerId));

        // 4) Trophies
        runIfNeeded("player-trophies:" + playerId, FRESH_TROPHIES,
                () -> trophyRepository.countByPlayerId(playerId) == 0,
                () -> trophiesSyncService.sync(playerId));

        // 5) Sidelined (tum kariyer)
        runIfNeeded("player-sidelined:" + playerId, FRESH_SIDELINED,
                () -> sidelinedRepository.findByPlayerIdOrderByStartDateDesc(playerId).isEmpty(),
                () -> sidelinedSyncService.syncOne(playerId));

        // 6) Transfers — kariyer; cogu sync 1-3sn surer, async background
        boolean transfersEmpty = transferRepository.findByPlayerIdOrderByTransferDateDesc(
                playerId).isEmpty();
        if (transfersEmpty) {
            // Cold start: senkron (yoksa hep bos gorur)
            runIfNeeded("player-transfers:" + playerId, FRESH_TRANSFERS,
                    () -> true,
                    () -> transfersSyncService.syncByPlayer(playerId));
        } else {
            // DB dolu: async background tazeleme
            self.refreshTransfersAsync(playerId);
        }

        // 7) Sezon stats — secili sezon icin
        Integer effectiveSeason = requestedSeason != null ? requestedSeason : currentSeason;
        if (effectiveSeason != null) {
            boolean isCurrentSeason = currentSeason != null
                    && currentSeason.equals(effectiveSeason);
            Duration freshness = isCurrentSeason ? FRESH_STATS_CURRENT : FRESH_STATS_OLD;
            runIfNeeded("player-stats:" + playerId + "-" + effectiveSeason, freshness,
                    () -> statRepository.countByPlayerIdAndSeason(
                            playerId, effectiveSeason) == 0,
                    () -> profileSyncService.sync(playerId, effectiveSeason));
        }
    }

    /**
     * YENİ oyuncu (DB'de HİÇ yok) akışı — {@link PlayerDetailService#getById}
     * çağırır. Bot/crawler yeni ID'leri gezerken request thread'lerini
     * doldurmasın diye:
     * <ul>
     *   <li>429 cooldown aktifse hiç başlama (thin/404 döner);</li>
     *   <li>aynı oyuncu yakında denendiyse atla ({@link #RECENT_ENSURE_GUARD});</li>
     *   <li>{@link #syncProfileGate} ({@value #SYNC_PROFILE_PERMITS} izin) dolu ise
     *       SENKRON atla → yığılma yerine hızlı thin/404;</li>
     *   <li>izin alınırsa YALNIZ profil (1 API çağrısı) senkron çekilir ki sayfa
     *       boş gitmesin; kariyer/kupa/transfer/stat ARKA PLANDA sınırlı havuzda.</li>
     * </ul>
     */
    public void ensureNewPlayer(Long playerId, Integer requestedSeason, Integer currentSeason) {
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return; // cooldown → boşuna deneme; DB'de yoksa 404, sonraki ziyaret toparlar
        }
        final Instant now = Instant.now();
        final Instant last = recentlyEnsured.get(playerId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return;
        }
        if (!syncProfileGate.tryAcquire()) {
            return; // eşzamanlı senkron tavan dolu → request thread'i kilitleme
        }
        try {
            recentlyEnsured.put(playerId, now);
            if (recentlyEnsured.size() > 20_000) recentlyEnsured.clear();
            // PROFİL (1 çağrı) SENKRON — sayfa boş gitmesin.
            runIfNeeded("player-profile:" + playerId, FRESH_PROFILE,
                    () -> !playerRepository.existsById(playerId),
                    () -> {
                        if (currentSeason != null) {
                            profileSyncService.sync(playerId, currentSeason);
                        }
                    });
            Player p = playerRepository.findById(playerId).orElse(null);
            if (p != null) markCoveredIfNeeded(p);
        } finally {
            syncProfileGate.release();
        }
        // Gerisini ARKA PLANDA (sınırlı havuz; sel altında DiscardPolicy ile düşer).
        lazyExecutor.execute(() -> {
            try {
                ensureRest(playerId, requestedSeason, currentSeason);
                evictPlayerCache(playerId);
            } catch (RuntimeException ex) {
                log.warn("Player rest arka plan hata playerId={}: {}", playerId, ex.getMessage());
            }
        });
    }

    /**
     * Fire-and-forget — veri DB'de zaten varken response'u BEKLETMEDEN arka
     * planda tazeler. Sync sonrasi cache evict → bir sonraki istek taze gorur.
     *
     * <p>Artık {@code @Async} DEĞİL: cooldown + recent-guard geçtikten sonra işi
     * ÖZEL sınırlı {@link #lazyExecutor}'a atar (stv-async'i bot seliyle
     * doldurmaz; kuyruk dolarsa DiscardPolicy ile fazlası düşer).
     */
    public void ensureForAsync(Long playerId, Integer requestedSeason, Integer currentSeason) {
        if (quotaTracker.cooldownRemainingMillis() > 0) {
            return; // cooldown → tazeleme atla (DB'de zaten veri var, sonra tazelenir)
        }
        final Instant now = Instant.now();
        final Instant last = recentlyEnsured.get(playerId);
        if (last != null && last.isAfter(now.minus(RECENT_ENSURE_GUARD))) {
            return; // yakında zaten tazeledik (bot aynı sayfayı döngüde tarar)
        }
        if (recentlyEnsured.size() > 20_000) recentlyEnsured.clear();
        recentlyEnsured.put(playerId, now);
        // ÖZEL sınırlı havuz (stv-async değil); kuyruk dolarsa DiscardPolicy düşürür.
        lazyExecutor.execute(() -> {
            try {
                ensureFor(playerId, requestedSeason, currentSeason);
                evictPlayerCache(playerId);
            } catch (RuntimeException ex) {
                log.warn("Async oyuncu ensure hatasi: playerId={} season={} — {}",
                        playerId, requestedSeason, ex.getMessage());
            }
        });
    }

    /** Auto-cover: ilk ziyarette covered=true → DailyPlayerRefreshJob girer. */
    @Transactional
    public void markCoveredIfNeeded(Player player) {
        if (player == null) return;
        Long playerId = player.getId();
        if (knownCoveredPlayers.contains(playerId)) return;
        if (player.isCovered()) {
            knownCoveredPlayers.add(playerId);
            return;
        }
        player.setCovered(true);
        playerRepository.save(player);
        knownCoveredPlayers.add(playerId);
        log.info("Player otomatik covered (ilk ziyaret): playerId={} '{}'",
                playerId, player.getName());
    }

    /**
     * Transfers async tazeleme (response bekletmesin). Sync sonrasi cache evict.
     */
    @Async
    public void refreshTransfersAsync(Long playerId) {
        try {
            long before = transferRepository.findByPlayerIdOrderByTransferDateDesc(
                    playerId).size();
            runIfNeeded("player-transfers:" + playerId, FRESH_TRANSFERS,
                    () -> transferRepository.findByPlayerIdOrderByTransferDateDesc(
                            playerId).isEmpty(),
                    () -> transfersSyncService.syncByPlayer(playerId));
            long after = transferRepository.findByPlayerIdOrderByTransferDateDesc(
                    playerId).size();
            if (after != before) {
                evictPlayerCache(playerId);
                log.info("Player transfers async refresh: playerId={} {} → {} satir, cache evict",
                        playerId, before, after);
            }
        } catch (RuntimeException ex) {
            log.warn("Player transfers async refresh hatasi (playerId={}): {}",
                    playerId, ex.getMessage());
        }
    }

    /** Sync sonrasi cache evict. */
    private void evictPlayerCache(Long playerId) {
        try {
            Cache cache = cacheManager.getCache(FootballCacheNames.LIVE);
            if (cache == null) return;
            // En sik kullanilan kombinasyonlari evict et — current sezon + tum diller.
            // Specifik sezon evict'i icin discovered season cache lazim, simdilik tum
            // bilinen sezonlar icin evict yapmiyoruz (cache 15sn TTL zaten kisa).
            for (String lang : new String[] {"tr", "en"}) {
                cache.evict("player-" + playerId + "-cur-" + lang);
            }
            List<Integer> seasons = statRepository.findSeasonYearsByPlayer(playerId);
            for (Integer season : seasons) {
                for (String lang : new String[] {"tr", "en"}) {
                    cache.evict("player-" + playerId + "-" + season + "-" + lang);
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Player cache evict hatasi (playerId={}): {}", playerId, ex.getMessage());
        }
    }

    /**
     * Module-spesifik sync mantigi: DB bos veya freshness window'u doldu mu?
     * Empty-debounce 10dk.
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
            log.warn("Player lazy sync API hatasi ({}): {}", key, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Player lazy sync beklenmedik hata ({}): {}", key, ex.getMessage());
        }
    }
}

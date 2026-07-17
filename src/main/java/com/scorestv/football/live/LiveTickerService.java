package com.scorestv.football.live;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixturePlayerStatsSyncService;
import com.scorestv.football.sync.FixtureStatisticsSyncService;
import com.scorestv.football.sync.FixtureUpserter;
import com.scorestv.football.sync.dto.FixtureApiDto;
import com.scorestv.mobile.notify.NotificationDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * API-Football'un {@code /fixtures?live=all} ucundan TÜM canlı maçları çeker,
 * önemli alanları değişen maçları saptar, DB'ye yazar ve değişimleri
 * {@link LiveBroadcaster} aracılığıyla STOMP/WebSocket üzerinden yayar.
 *
 * <p>Akış:
 * <ol>
 *   <li>API çağrısı (transaction dışında — yavaş ağ DB'yi kilitlemez)</li>
 *   <li>Gelen id'lerin DB'deki mevcut "snapshot"larını topla</li>
 *   <li>{@link FixtureUpserter} ile upsert (kendi transaction'ında)</li>
 *   <li>API verisini önceki snapshot ile karşılaştır → değişenleri seç</li>
 *   <li>Değişen maçları JOIN FETCH ile tam yükleyip broadcaster'a ver</li>
 * </ol>
 *
 * <p>Bu sınıf transactional <b>değildir</b>. DB yazımı {@code upserter.upsert}'in
 * kendi {@code @Transactional} sınırında olur; karşılaştırma ve yayın o
 * transaction'ın commit'inden sonra yapılır, böylece istemcilere geri alınmış
 * değişim yansımaz.
 */
@Service
public class LiveTickerService {

    private static final Logger log = LoggerFactory.getLogger(LiveTickerService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<FixtureApiDto>>>
            FIXTURES_TYPE = new ParameterizedTypeReference<>() {
            };

    /**
     * Stuck-LIVE detection icin: DB'deki "su an oynaniyor" sayilan kodlar.
     * API'nin live response'unda olmayan ama DB'de bu statulerde olan
     * fixture'lar "sikismis" (muhtemelen FT'ye gecmis ama bizim tarafa
     * yansimamis) — tek tek /fixtures?id ile alinip guncellenir.
     */
    private static final Set<String> LIVE_DB_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE");

    /**
     * Stuck-LIVE fixtures icin bulk fetch chunk boyu. API-Football
     * {@code /fixtures?ids=A-B-C} en fazla 20 id'yi tek istekte destekler.
     */
    private static final int STUCK_BATCH_SIZE = 20;

    /**
     * Bir maçın normal maksimum süresi (2×45 dk + iki devre arası + buffer).
     * Bundan eski kickoff'lu hâlâ "canlı" statüde olan maçlar API'den zorla
     * yeniden fetch edilir — uzatma + penaltı oluyorsa API doğru status
     * döner; bitmiş ama backend yetişmemişse FT'ye geçer.
     *
     * <p>95dk = 90dk normal sure + 5dk buffer. Cogu mac bu sure icinde biter;
     * uzatma + penalti olan istisnalar tekrar fetch'te dogru status alir
     * (API hala 1H/2H/ET dondurur, biz de oyle birakirizsin).
     */
    private static final Duration MAX_LIVE_DURATION = Duration.ofMinutes(95);

    private final ApiFootballClient client;
    private final FixtureUpserter upserter;
    private final FixtureRepository fixtureRepository;
    private final LiveBroadcaster broadcaster;
    private final FixtureEventsLiveProcessor eventsProcessor;
    private final FixtureStatisticsSyncService statsSyncService;
    private final FixturePlayerStatsSyncService playerStatsSyncService;
    private final SyncRateLimiter rateLimiter;
    private final NotificationDispatcherService notificationDispatcher;
    private final MatchStatusNotifier statusNotifier;
    private final com.scorestv.football.detail.FixtureDetailCacheEvictor detailCacheEvictor;

    public LiveTickerService(ApiFootballClient client,
                             FixtureUpserter upserter,
                             FixtureRepository fixtureRepository,
                             LiveBroadcaster broadcaster,
                             FixtureEventsLiveProcessor eventsProcessor,
                             FixtureStatisticsSyncService statsSyncService,
                             FixturePlayerStatsSyncService playerStatsSyncService,
                             SyncRateLimiter rateLimiter,
                             NotificationDispatcherService notificationDispatcher,
                             MatchStatusNotifier statusNotifier,
                             com.scorestv.football.detail.FixtureDetailCacheEvictor detailCacheEvictor) {
        this.client = client;
        this.upserter = upserter;
        this.fixtureRepository = fixtureRepository;
        this.broadcaster = broadcaster;
        this.eventsProcessor = eventsProcessor;
        this.statsSyncService = statsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.rateLimiter = rateLimiter;
        this.notificationDispatcher = notificationDispatcher;
        this.statusNotifier = statusNotifier;
        this.detailCacheEvictor = detailCacheEvictor;
    }

    /**
     * Bir tick çevrimini çalıştırır. API'den 0 canlı maç dönerse hızlıca çıkar.
     *
     * <p><b>Akış:</b>
     * <ol>
     *   <li>{@code /fixtures?live=all} — su an canli olanlar</li>
     *   <li><b>Stuck-LIVE detection:</b> DB'de LIVE ama API live response'da
     *       olmayanlar = muhtemelen FT'ye gecmis ama listeden dusmus. Tek tek
     *       {@code /fixtures?id=X} ile cekilir.</li>
     *   <li>Tum items icin DB pre-snapshot (upsert ONCESI)</li>
     *   <li>Upsert (live + stuck birlikte)</li>
     *   <li>Snapshot karsilastirma → degisenleri broadcast</li>
     * </ol>
     */
    public LiveTickerResult tick() {
        // 1) /fixtures?live=all — su an oynanan maclar.
        ApiFootballResponse<List<FixtureApiDto>> response = client.get(
                "/fixtures", Map.of("live", "all"), FIXTURES_TYPE);
        List<FixtureApiDto> liveItems = response.response() != null
                ? response.response()
                : List.of();

        Set<Long> liveApiIds = new HashSet<>();
        for (FixtureApiDto item : liveItems) {
            if (item.fixture() != null && item.fixture().id() != null) {
                liveApiIds.add(item.fixture().id());
            }
        }

        // 2) Stuck-LIVE iki kaynak:
        //    a) DB'de LIVE ama API live=all listesinde yok — sikismis (FT/PST/CANC)
        //    b) DB'de LIVE + kickoff'tan 105dk gecmis (API hala canli diyor
        //       olabilir ama zaman asiyor — gerceklik FT olmali)
        Set<Long> stuckIdsSet = new LinkedHashSet<>();
        for (Long id : fixtureRepository.findIdsByStatusShortIn(LIVE_DB_STATUSES)) {
            if (!liveApiIds.contains(id)) stuckIdsSet.add(id);
        }
        Instant agedCutoff = Instant.now().minus(MAX_LIVE_DURATION);
        for (Long id : fixtureRepository.findAgedLiveIds(LIVE_DB_STATUSES, agedCutoff)) {
            stuckIdsSet.add(id); // duplicate'i set kendisi yutar
        }
        List<Long> stuckIds = List.copyOf(stuckIdsSet);
        if (!stuckIds.isEmpty()) {
            log.info("Stuck-LIVE tespit: {} fixture (live-list dışı + yaşlı canlı)",
                    stuckIds.size());
        }

        // 3) ONCESI snapshot — DB'nin upsert ONCESI hali (broadcasting'in
        // dogru karsilastirmasi icin sart).
        Set<Long> allIds = new HashSet<>(liveApiIds);
        allIds.addAll(stuckIds);
        Map<Long, Snapshot> before = new HashMap<>();
        if (!allIds.isEmpty()) {
            for (Fixture fixture : fixtureRepository.findAllById(allIds)) {
                before.put(fixture.getId(), Snapshot.of(fixture));
            }
        }

        // 4) Stuck'lari bulk cek + tum items listesini olustur.
        // API: /fixtures?ids=A-B-C (max 20 id/cagri). 10 stuck = 1 cagri,
        // 21+ stuck = 2+ cagri. Tek-tek cagriya gore quota cok daha verimli.
        List<FixtureApiDto> items = new java.util.ArrayList<>(liveItems);
        if (!stuckIds.isEmpty()) {
            for (int from = 0; from < stuckIds.size(); from += STUCK_BATCH_SIZE) {
                int to = Math.min(from + STUCK_BATCH_SIZE, stuckIds.size());
                List<Long> chunk = stuckIds.subList(from, to);
                String idsParam = chunk.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining("-"));
                try {
                    ApiFootballResponse<List<FixtureApiDto>> bulkResp = client.get(
                            "/fixtures", Map.of("ids", idsParam), FIXTURES_TYPE);
                    if (bulkResp.response() != null) {
                        items.addAll(bulkResp.response());
                    }
                } catch (RuntimeException ex) {
                    log.warn("Stuck bulk fetch basarisiz: chunk={} (size {}) — {}",
                            idsParam, chunk.size(), ex.getMessage());
                }
            }
        }

        if (items.isEmpty()) {
            log.debug("Canli tick: 0 mac (ne live ne stuck).");
            return new LiveTickerResult(0, 0);
        }

        // 5) Tek seferde tum items (live + stuck) upsert.
        upserter.upsert(items);

        // 5a) Maç başladı/bitti bildirimleri — ASENKRON + KALICI tam-bir-kez.
        // fire-and-forget: bildirim claim'i + FCM ayrı thread'de koşar, bu
        // hız-kritik canlı-skor tick'ini ASLA sektmez/yavaşlatmaz. Doğruluk
        // (tam-bir-kez) atomik claim ile korunur. Upsert sonrası çağrılır ki
        // fixture satırları DB'de mevcut olsun (claim UPDATE için).
        statusNotifier.notifyStatusTransitions(items);

        // 4) Değişen id'leri saptayan karşılaştırma — yeni gelen API verisi
        //    ile önceki DB snapshot eşit değilse o maç değişmiş demektir.
        //    Ayrıca SKOR değişimi yaşayanları ayrı toplayıp anında events
        //    sync'i tetikleriz (gol → oyuncu adıyla mesaj ~15sn'de düşer).
        Set<Long> changedIds = new HashSet<>();
        Set<Long> scoreChangedIds = new HashSet<>();
        // Skor VEYA status değişen maçlar → detay Redis cache'i evict edilecek.
        // Dakika (elapsed) değişimi HARİÇ: o WS + lokal sayaçla gelir, cache'i
        // her tick evict etmeye gerek yok (cache canlı maçta işe yarar kalır).
        Set<Long> detailEvictIds = new HashSet<>();
        for (FixtureApiDto item : items) {
            if (item.fixture() == null || item.fixture().id() == null) {
                continue;
            }
            Long id = item.fixture().id();
            Snapshot incoming = Snapshot.of(item);
            Snapshot previous = before.get(id);
            if (previous == null || !previous.equals(incoming)) {
                changedIds.add(id);
                if (previous != null) {
                    boolean sc = scoreChanged(previous, incoming);
                    boolean stc = !Objects.equals(
                            previous.statusShort(), incoming.statusShort());
                    if (sc) {
                        scoreChangedIds.add(id);
                    }
                    if (sc || stc) {
                        detailEvictIds.add(id);
                    }
                }
            }
        }
        if (changedIds.isEmpty()) {
            log.debug("Canlı tick: {} maç çekildi, değişen yok.", items.size());
            return new LiveTickerResult(items.size(), 0);
        }

        // 5) Değişenleri lig + takım ilişkileriyle tam yükleyip yay.
        List<Fixture> updated = fixtureRepository.findAllByIdWithDetails(changedIds);
        broadcaster.broadcastAll(updated);

        // 5b) COHERENCY: skor/status değişen maçların detay Redis cache'ini
        // (af-live → detail-{id}-...) evict et. Böylece kullanıcı bir sonraki
        // açılışta / WS "data-ready" refetch'inde DAİMA taze skoru görür —
        // api-football'a hiç gitmeden, "Yenile"ye basmadan. Bu, canlı-veri
        // cache tutarsızlığının ("skor değişti ama detay eski gösteriyor")
        // kökten çözümüdür. Ağ/Redis maliyeti düşük: yalnız gerçek değişimde,
        // dakika tick'lerinde değil.
        for (Long id : detailEvictIds) {
            detailCacheEvictor.evictAll(id);
        }

        // 6) Skor değişimi olan maçlar için ANINDA events + stats + player_stats
        //    sync — periyodik joblar beklenmeden gol "(Andrada 25')" oyuncu adıyla,
        //    güncel possession/shots ve player rating'ler ~15sn içinde gelir.
        //    Rate limiter timestamp'leri güncellenir ki periyodik joblar boş yere
        //    aynı maçı tekrar yoklamasın.
        for (Long id : scoreChangedIds) {
            // ANINDA gol bildirimi — skor deltasindan gol atan takimi belirle ve
            // beklemeden gonder. Golcu o an DB'de varsa dispatchGoal ekler; yoksa
            // skor-only gider (rekabet icin hiz oncelikli, event beklenmez).
            try {
                final Snapshot prev = before.get(id);
                final Fixture fx = updated.stream()
                        .filter(u -> id.equals(u.getId()))
                        .findFirst()
                        .orElse(null);
                if (prev != null && fx != null) {
                    Long scoringTeamId = null;
                    if (_increased(fx.getHomeGoals(), prev.homeGoals())) {
                        scoringTeamId = fx.getHomeTeam().getId();
                    } else if (_increased(fx.getAwayGoals(), prev.awayGoals())) {
                        scoringTeamId = fx.getAwayTeam().getId();
                    }
                    if (scoringTeamId != null) {
                        notificationDispatcher.dispatchGoal(id, scoringTeamId);
                    } else if (_decreased(fx.getHomeGoals(), prev.homeGoals())) {
                        // SKOR DÜŞTÜ → gol iptal (VAR). İptal edilen golün no'su =
                        // önceki (yüksek) sayaç. Aynı collapse slotuyla cihazdaki
                        // "GOL!" kartını "Gol iptal!" olarak günceller.
                        notificationDispatcher.dispatchGoalCancelled(
                                id, fx.getHomeTeam().getId(), prev.homeGoals());
                    } else if (_decreased(fx.getAwayGoals(), prev.awayGoals())) {
                        notificationDispatcher.dispatchGoalCancelled(
                                id, fx.getAwayTeam().getId(), prev.awayGoals());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("FCM skor-gol dispatch hata fixtureId={}: {}",
                        id, ex.getMessage());
            }
            // Gol sonrası: bu maçın EVENTS senkronunu ~90sn HIZLANDIR (non-covered
            // 60sn çarpanını bypass et). API golcü adını gecikmeli yazdığı için,
            // hızlı yoklamayla golcü adı (faz-2 sessiz güncelleme) ~15sn'de düşer,
            // ~2 dk sonra değil. Süre bitince normale döner (kota korunur).
            rateLimiter.boostEvents(id, Duration.ofSeconds(90));
            // Batch modu açıksa per-fixture detay çağrılarını ATLA — canlı
            // detay artık LiveDetailBatchJob'un tek /fixtures?ids= çağrısıyla
            // gelir (18'lik burst → 0; hızlı gol FCM'i yukarıda zaten gitti).
            if (rateLimiter.isLiveBundleEnabled()) {
                continue;
            }
            try {
                eventsProcessor.syncAndBroadcast(id);
                rateLimiter.markSynced(SyncRateLimiter.SyncType.EVENTS, id);
            } catch (RuntimeException ex) {
                log.warn("Skor-tetikli events sync başarısız: fixtureId={} — {}",
                        id, ex.getMessage());
            }
            try {
                statsSyncService.sync(id);
                rateLimiter.markSynced(SyncRateLimiter.SyncType.STATISTICS, id);
            } catch (RuntimeException ex) {
                log.warn("Skor-tetikli stats sync başarısız: fixtureId={} — {}",
                        id, ex.getMessage());
            }
            try {
                playerStatsSyncService.sync(id);
                rateLimiter.markSynced(SyncRateLimiter.SyncType.PLAYER_STATS, id);
            } catch (RuntimeException ex) {
                log.warn("Skor-tetikli player_stats sync başarısız: fixtureId={} — {}",
                        id, ex.getMessage());
            }
        }

        log.info("Canlı tick: {} maç, {} değişim ({} skor) WebSocket'e yayıldı.",
                items.size(), updated.size(), scoreChangedIds.size());
        return new LiveTickerResult(items.size(), updated.size());
    }

    /** Skor değişimi (gol) tespiti — homeGoals ya da awayGoals farklı mı? */
    private static boolean scoreChanged(Snapshot prev, Snapshot now) {
        return !Objects.equals(prev.homeGoals(), now.homeGoals())
                || !Objects.equals(prev.awayGoals(), now.awayGoals());
    }

    /** Skor ARTTI mi? (gol). VAR ile iptal/azalma bildirim üretmez. */
    private static boolean _increased(Integer now, Integer prev) {
        final int n = now == null ? 0 : now;
        final int p = prev == null ? 0 : prev;
        return n > p;
    }

    /** Skor DÜŞTÜ mü — VAR gol iptali tespiti (now &lt; prev). */
    private static boolean _decreased(Integer now, Integer prev) {
        final int n = now == null ? 0 : now;
        final int p = prev == null ? 0 : prev;
        return n < p;
    }

    /**
     * Yayın için önemli olan alanların anlık fotoğrafı: durum kodu, geçen
     * dakika, uzatma dakikası ve skor. İki snapshot eşit değilse maç değişmiş
     * demektir; record'un otomatik {@code equals}'ı tam karşılaştırma yapar.
     */
    private record Snapshot(
            String statusShort,
            Integer elapsed,
            Integer statusExtra,
            Integer homeGoals,
            Integer awayGoals
    ) {
        static Snapshot of(Fixture fixture) {
            return new Snapshot(
                    fixture.getStatusShort(),
                    fixture.getElapsed(),
                    fixture.getStatusExtra(),
                    fixture.getHomeGoals(),
                    fixture.getAwayGoals());
        }

        static Snapshot of(FixtureApiDto item) {
            FixtureApiDto.Fixture f = item.fixture();
            FixtureApiDto.Status s = (f == null) ? null : f.status();
            FixtureApiDto.Goals g = item.goals();
            return new Snapshot(
                    s == null ? null : s.shortCode(),
                    s == null ? null : s.elapsed(),
                    s == null ? null : s.extra(),
                    g == null ? null : g.home(),
                    g == null ? null : g.away());
        }
    }
}

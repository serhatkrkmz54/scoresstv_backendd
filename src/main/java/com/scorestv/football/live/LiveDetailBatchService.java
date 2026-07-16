package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballClient.RequestPriority;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixturePlayerStatsSyncService;
import com.scorestv.football.sync.FixtureStatisticsSyncService;
import com.scorestv.football.sync.dto.FixtureApiDto;
import com.scorestv.football.sync.dto.FixtureBundleApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canlı maç detayını (events + statistics + players) TEK
 * {@code GET /fixtures?ids=A-B-C} çağrısıyla, 20'lik gruplar hâlinde çeken batch.
 *
 * <p><b>Neden:</b> API-Football {@code ?ids=} sorgusunda her maçın events/
 * statistics/players'ını GÖMÜLÜ döndürür. Böylece per-fixture ayrı 3 çağrı
 * (LiveEvents+LiveStatistics+LivePlayerStats) yerine 20 maç TEK istekte gelir —
 * canlı kota ~25× düşer ve saniye-burst (dolayısıyla 429 cooldown spirali)
 * tamamen biter. Yalnız {@code scorestv.football.sync.live-bundle-enabled=true}
 * iken {@link LiveDetailBatchJob} tarafından çağrılır.
 *
 * <p><b>Veri güvenliği:</b> düşük-coverage ligler statistics/players'ı boş
 * dizi döndürür (ayrı uçlar da aynı boşu verirdi). Batch boş dizide upsert'i
 * ATLAR → mevcut veri korunur, transient boş yanıt veriyi silmez. Events akışı
 * {@link FixtureEventsLiveProcessor} üzerinden gider — WebSocket yayını + FCM
 * dispatch (gol/kart) aynen korunur.
 */
@Component
public class LiveDetailBatchService {

    private static final Logger log = LoggerFactory.getLogger(LiveDetailBatchService.class);

    /** "Şu an oynanıyor" sayılan API-Football durum kodları. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    /** API-Football {@code ?ids=} tek çağrıda en fazla 20 id destekler. */
    private static final int MAX_BATCH = 20;

    private static final ParameterizedTypeReference<ApiFootballResponse<List<FixtureBundleApiDto>>>
            BUNDLE_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final FixtureRepository fixtureRepository;
    private final FixtureEventsLiveProcessor eventsProcessor;
    private final FixtureStatisticsSyncService statsSyncService;
    private final FixturePlayerStatsSyncService playerStatsSyncService;
    private final FootballProperties properties;
    private final LiveBroadcaster broadcaster;

    public LiveDetailBatchService(ApiFootballClient client,
                                  FixtureRepository fixtureRepository,
                                  FixtureEventsLiveProcessor eventsProcessor,
                                  FixtureStatisticsSyncService statsSyncService,
                                  FixturePlayerStatsSyncService playerStatsSyncService,
                                  FootballProperties properties,
                                  LiveBroadcaster broadcaster) {
        this.client = client;
        this.fixtureRepository = fixtureRepository;
        this.eventsProcessor = eventsProcessor;
        this.statsSyncService = statsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.properties = properties;
        this.broadcaster = broadcaster;
    }

    /**
     * Şu an canlı olan tüm maçların detayını 20'lik bundle çağrılarıyla çeker.
     *
     * @return detayı işlenen maç sayısı
     */
    public int run() {
        List<Long> liveIds = fixtureRepository.findIdsByStatusShortIn(LIVE_STATUSES);
        if (liveIds == null || liveIds.isEmpty()) {
            return 0;
        }
        int batchSize = Math.min(MAX_BATCH,
                Math.max(1, properties.sync().liveBundleBatchSize()));

        int processed = 0;
        // Bundle'ın taze fixture bloğundan (elapsed/extra) canlı saat güncellemeleri.
        List<ClockUpdate> clockUpdates = new ArrayList<>();
        for (int from = 0; from < liveIds.size(); from += batchSize) {
            List<Long> chunk = liveIds.subList(from, Math.min(from + batchSize, liveIds.size()));
            String idsParam = chunk.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("-"));
            try {
                ApiFootballResponse<List<FixtureBundleApiDto>> resp = client.get(
                        "/fixtures", Map.of("ids", idsParam), BUNDLE_TYPE, RequestPriority.LIVE);
                List<FixtureBundleApiDto> items = resp.response();
                if (items == null) {
                    continue;
                }
                for (FixtureBundleApiDto item : items) {
                    processed += processOne(item);
                    ClockUpdate cu = clockOf(item);
                    if (cu != null) clockUpdates.add(cu);
                }
            } catch (ApiException ex) {
                // 429 cooldown / upstream — bu tick'i bırak, kalan chunk'ları ZORLAMA
                // (spiral'i önler; bir sonraki 15 sn tick'inde tekrar denenir).
                log.warn("Canlı detay batch (API): {} — kalan chunk atlandı.", ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                log.error("Canlı detay batch beklenmedik hata (from=" + from + ")", ex);
            }
        }
        applyClockUpdates(clockUpdates);
        return processed;
    }

    /** Bundle fixture bloğundan canlı saat anlık görüntüsü (null-güvenli). */
    private ClockUpdate clockOf(FixtureBundleApiDto item) {
        if (item == null || item.fixture() == null || item.fixture().id() == null
                || item.fixture().status() == null) {
            return null;
        }
        FixtureApiDto.Status st = item.fixture().status();
        return new ClockUpdate(item.fixture().id(), st.elapsed(), st.extra(), st.shortCode());
    }

    /**
     * Bundle'dan gelen canlı dakikayı (elapsed/extra) fixture'a yazar ve WS'e yayar.
     *
     * <p><b>Neden gerek var:</b> fixture.elapsed'i normalde SADECE
     * {@link LiveTickerService} ({@code /fixtures?live=all}) yazar. Uzatmada bu
     * kaynak bazı maçları geç/eksik verince dakika "95" gibi bir değerde donar;
     * oysa event'ler (bu bundle'dan) 105'e ilerler. Bundle'ın per-maç
     * {@code ?ids=} fixture bloğu en az live=all kadar tazedir — dakikayı buradan
     * da tazeleriz.
     *
     * <p>API'den ({@code ?ids=} fixture bloğu) ne geldiyse AYNEN yansıtılır —
     * clamp/uydurma yok. Yalnız değer değiştiyse yazılıp yayılır.
     */
    private void applyClockUpdates(List<ClockUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        Map<Long, ClockUpdate> byId = new HashMap<>();
        for (ClockUpdate u : updates) {
            byId.put(u.id(), u); // aynı tick'te son değer kazanır
        }
        List<Fixture> fixtures = fixtureRepository.findAllByIdWithDetails(byId.keySet());
        List<Fixture> changed = new ArrayList<>();
        for (Fixture f : fixtures) {
            ClockUpdate u = byId.get(f.getId());
            if (u == null || u.elapsed() == null) {
                continue; // API dakika vermediyse dokunma (mevcut değeri boşa çıkarma).
            }
            // API'den (?ids= fixture bloğu) ne geldiyse AYNEN yansıt — clamp/uydurma
            // YOK. Yalnız DEĞİŞTİYSE yaz + yay (gereksiz DB yazımı/broadcast olmasın).
            if (Objects.equals(f.getElapsed(), u.elapsed())
                    && Objects.equals(f.getStatusExtra(), u.extra())) {
                continue;
            }
            f.setElapsed(u.elapsed());
            f.setStatusExtra(u.extra());
            changed.add(f);
        }
        if (changed.isEmpty()) {
            return;
        }
        try {
            fixtureRepository.saveAll(changed);
            broadcaster.broadcastAll(changed);
            log.debug("Canlı detay batch: {} maç dakikası API değeriyle güncellendi + yayıldı.",
                    changed.size());
        } catch (RuntimeException ex) {
            // Eşzamanlı LiveTicker yazımı (opt. lock) vb. — bir sonraki tick düzeltir.
            log.warn("Canlı detay saat güncellemesi yazılamadı: {}", ex.getMessage());
        }
    }

    /** Tek maçın bundle'dan gelen canlı saat anlık görüntüsü. */
    private record ClockUpdate(Long id, Integer elapsed, Integer extra, String shortCode) {}

    /**
     * Tek maçın gömülü detayını ilgili upsert'lere dağıtır. Her blok BOŞSA atlar
     * (coverage/transient boş yanıtta mevcut veriyi SİLMEZ).
     */
    private int processOne(FixtureBundleApiDto item) {
        if (item == null || item.fixture() == null || item.fixture().id() == null) {
            return 0;
        }
        Long fixtureId = item.fixture().id();
        try {
            if (item.events() != null && !item.events().isEmpty()) {
                eventsProcessor.syncAndBroadcast(fixtureId, item.events());
            }
            if (item.statistics() != null && !item.statistics().isEmpty()) {
                statsSyncService.sync(fixtureId, item.statistics());
            }
            if (item.players() != null && !item.players().isEmpty()) {
                playerStatsSyncService.sync(fixtureId, item.players());
            }
            return 1;
        } catch (RuntimeException ex) {
            log.warn("Canlı detay dağıtımı hata fixtureId={}: {}", fixtureId, ex.getMessage());
            return 0;
        }
    }
}
package com.scorestv.football.live;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballClient.RequestPriority;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixturePlayerStatsSyncService;
import com.scorestv.football.sync.FixtureStatisticsSyncService;
import com.scorestv.football.sync.dto.FixtureBundleApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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

    public LiveDetailBatchService(ApiFootballClient client,
                                  FixtureRepository fixtureRepository,
                                  FixtureEventsLiveProcessor eventsProcessor,
                                  FixtureStatisticsSyncService statsSyncService,
                                  FixturePlayerStatsSyncService playerStatsSyncService,
                                  FootballProperties properties) {
        this.client = client;
        this.fixtureRepository = fixtureRepository;
        this.eventsProcessor = eventsProcessor;
        this.statsSyncService = statsSyncService;
        this.playerStatsSyncService = playerStatsSyncService;
        this.properties = properties;
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
        return processed;
    }

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

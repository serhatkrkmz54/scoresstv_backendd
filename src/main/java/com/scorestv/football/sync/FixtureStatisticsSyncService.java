package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.StatisticApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın istatistiklerini API-Football'dan çekip DB'ye senkronlar.
 *
 * <p>Çağrı: {@code GET /fixtures/statistics?fixture={id}}. API ucu dakika
 * cadence'i ile güncellenir; periyodik {@link com.scorestv.football.live.LiveStatisticsJob}
 * canlı maçlar için bu metodu çağırır.
 */
@Service
public class FixtureStatisticsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureStatisticsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<StatisticApiDto>>>
            STATS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final FixtureStatisticsUpserter upserter;

    public FixtureStatisticsSyncService(ApiFootballClient client,
                                        FixtureStatisticsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public FixtureStatisticsSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<StatisticApiDto>> response = client.get(
                "/fixtures/statistics", Map.of("fixture", fixtureId), STATS_TYPE);
        List<StatisticApiDto> items = response.response();
        int written = upserter.replace(fixtureId, items == null ? List.of() : items);
        if (written > 0) {
            log.info("İstatistik senkronu: fixtureId={} — {} satır yazıldı",
                    fixtureId, written);
        }
        return new FixtureStatisticsSyncResult(fixtureId, written);
    }
}

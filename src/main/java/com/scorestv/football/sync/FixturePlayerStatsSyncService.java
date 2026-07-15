package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.PlayerStatApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın oyuncu istatistiklerini API-Football'dan çekip DB'ye senkronlar.
 * Çağrı: {@code GET /fixtures/players?fixture={id}}.
 *
 * <p>API ucu dakika cadence'ı ile güncellenir; periyodik
 * {@link com.scorestv.football.live.LivePlayerStatsJob} canlı maçlar için
 * bu metodu çağırır (varsayılan 120 sn).
 */
@Service
public class FixturePlayerStatsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixturePlayerStatsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<PlayerStatApiDto>>>
            PLAYERS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final FixturePlayerStatsUpserter upserter;

    public FixturePlayerStatsSyncService(ApiFootballClient client,
                                         FixturePlayerStatsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public FixturePlayerStatsSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<PlayerStatApiDto>> response = client.get(
                "/fixtures/players", Map.of("fixture", fixtureId), PLAYERS_TYPE);
        return sync(fixtureId, response.response());
    }

    /**
     * Bundle'dan ({@code /fixtures?ids=}) gelen ÖNCEDEN ÇEKİLMİŞ oyuncu-istatistik
     * listesiyle upsert — API çağrısı YAPMAZ. Canlı detay batch'i kullanır.
     */
    public FixturePlayerStatsSyncResult sync(Long fixtureId, List<PlayerStatApiDto> items) {
        int written = upserter.replace(fixtureId, items == null ? List.of() : items);
        if (written > 0) {
            log.info("Oyuncu istatistik senkronu: fixtureId={} — {} oyuncu satırı yazıldı",
                    fixtureId, written);
        }
        return new FixturePlayerStatsSyncResult(fixtureId, written);
    }
}

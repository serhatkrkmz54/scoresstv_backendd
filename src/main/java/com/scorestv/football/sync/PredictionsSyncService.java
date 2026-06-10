package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.PredictionApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın tahminini API-Football'dan çekip DB'ye senkronlar.
 * Çağrı: {@code GET /predictions?fixture={id}}.
 *
 * <p>API frekansı saatte bir. Genelde maç öncesi anlamlıdır; başlayan maç için
 * tahmin sabitlenir. DailyPredictionsJob yarınki covered maçlar için pre-fetch
 * yapar.
 */
@Service
public class PredictionsSyncService {

    private static final Logger log = LoggerFactory.getLogger(PredictionsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<PredictionApiDto>>>
            PRED_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final PredictionsUpserter upserter;

    public PredictionsSyncService(ApiFootballClient client, PredictionsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public PredictionsSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<PredictionApiDto>> response = client.get(
                "/predictions", Map.of("fixture", fixtureId), PRED_TYPE);
        List<PredictionApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.debug("Tahmin boş: fixtureId={}", fixtureId);
            return new PredictionsSyncResult(fixtureId, 0);
        }
        int written = upserter.upsert(fixtureId, items.get(0));
        if (written > 0) {
            log.info("Tahmin senkronu: fixtureId={} — upsert OK", fixtureId);
        }
        return new PredictionsSyncResult(fixtureId, written);
    }
}

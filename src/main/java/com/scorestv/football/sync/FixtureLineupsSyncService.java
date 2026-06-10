package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.LineupApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir maçın kadrolarını API-Football'dan çekip DB'ye senkronlar.
 *
 * <p>Çağrı: {@code GET /fixtures/lineups?fixture={id}}. Maç başlamadan
 * 20-40 dk önce dolu sonuç vermeye başlar (alt liglerde maç sonrası
 * gecikmeli olabilir).
 *
 * <p>HTTP çağrısı transaction dışında, DB yazımı {@link FixtureLineupsUpserter}'ın
 * kendi transaction'ında. Boş yanıt = işlem yok (announced_at korunur, var olan
 * kayıt değişmez).
 */
@Service
public class FixtureLineupsSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureLineupsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<LineupApiDto>>>
            LINEUPS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final FixtureLineupsUpserter upserter;

    public FixtureLineupsSyncService(ApiFootballClient client,
                                     FixtureLineupsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public FixtureLineupsSyncResult sync(Long fixtureId) {
        ApiFootballResponse<List<LineupApiDto>> response = client.get(
                "/fixtures/lineups", Map.of("fixture", fixtureId), LINEUPS_TYPE);
        List<LineupApiDto> items = response.response();
        int written = upserter.upsert(fixtureId, items == null ? List.of() : items);
        if (written > 0) {
            log.info("Kadro senkronu: fixtureId={} — {} takım kadrosu yazıldı",
                    fixtureId, written);
        }
        return new FixtureLineupsSyncResult(fixtureId, written);
    }
}

package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.StandingApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir lig + sezonun puan durumunu API-Football'dan çekip DB'ye senkronlar.
 *
 * <p>Çağrı: {@code GET /standings?league={X}&season={Y}}. API yanıtı tek
 * elemanlı liste içinde {@code league.standings} (iki-iç-içe array — grup
 * × sıra).
 */
@Service
public class StandingsSyncService {

    private static final Logger log = LoggerFactory.getLogger(StandingsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<StandingApiDto>>>
            STANDINGS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final StandingsUpserter upserter;

    public StandingsSyncService(ApiFootballClient client, StandingsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public StandingsSyncResult sync(Long leagueId, Integer season) {
        ApiFootballResponse<List<StandingApiDto>> response = client.get(
                "/standings",
                Map.of("league", leagueId, "season", season),
                STANDINGS_TYPE);
        List<StandingApiDto> items = response.response();
        if (items == null || items.isEmpty()
                || items.get(0).league() == null
                || items.get(0).league().standings() == null) {
            log.debug("Puan durumu boş: leagueId={} season={}", leagueId, season);
            return new StandingsSyncResult(leagueId, season, 0);
        }
        int written = upserter.replace(leagueId, season, items.get(0).league().standings());
        log.info("Puan durumu senkronu: leagueId={} season={} — {} satır",
                leagueId, season, written);
        return new StandingsSyncResult(leagueId, season, written);
    }
}

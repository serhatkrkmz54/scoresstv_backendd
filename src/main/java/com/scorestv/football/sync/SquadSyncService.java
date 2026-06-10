package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.SquadApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir takimin GUNCEL kadrosunu API-Football'dan ceker:
 *   {@code GET /players/squads?team=X}
 *
 * <p>API yalnizca guncel kadroyu doner; eski sezonlari icin {@code
 * /players?team=X&season=Y} sayfa sayfa cagrilmali (cok agir, simdilik atla).
 */
@Service
public class SquadSyncService {

    private static final Logger log = LoggerFactory.getLogger(SquadSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<SquadApiDto>>>
            SQUAD_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final SquadUpserter upserter;

    public SquadSyncService(ApiFootballClient client, SquadUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    /**
     * Bir takimin guncel kadrosunu cek + DB'ye yaz.
     *
     * @param season Database'e snapshot olarak yazilacak sezon (genelde
     *               takimin current'i)
     * @return yazilan oyuncu sayisi
     */
    public int sync(Long teamId, Integer season) {
        ApiFootballResponse<List<SquadApiDto>> response = client.get(
                "/players/squads", Map.of("team", teamId), SQUAD_TYPE);
        List<SquadApiDto> items = response.response();
        if (items == null || items.isEmpty() || items.getFirst().players() == null) {
            log.info("Squad sync: bos yanit teamId={}", teamId);
            return 0;
        }
        int written = upserter.replace(teamId, season, items.getFirst().players());
        log.info("Squad sync: teamId={} season={} — {} oyuncu", teamId, season, written);
        return written;
    }
}

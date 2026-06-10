package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.PlayerCareerTeamApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Oyuncunun kariyer takimlarini (sezonlariyla birlikte) API'den ceker:
 *   {@code GET /players/teams?player=X}
 *
 * <p>Yanit: oyuncunun oynadigi her takim icin {team, seasons[]} objesi.
 * Player detay sayfasi:
 * <ul>
 *   <li>Sezon dropdown'unu doldurur (tum yillarin union'i)</li>
 *   <li>Kariyer takimlari widget'i — "Sahin: Basaksehir, Konyaspor, Türkiye, ..."</li>
 * </ul>
 */
@Service
public class PlayerCareerTeamsSyncService {

    private static final Logger log = LoggerFactory.getLogger(PlayerCareerTeamsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<PlayerCareerTeamApiDto>>>
            CAREER_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final PlayerCareerTeamsUpserter upserter;

    public PlayerCareerTeamsSyncService(ApiFootballClient client,
                                        PlayerCareerTeamsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public int sync(Long playerId) {
        if (playerId == null) return 0;
        ApiFootballResponse<List<PlayerCareerTeamApiDto>> response = client.get(
                "/players/teams", Map.of("player", playerId), CAREER_TYPE);
        int written = upserter.upsert(playerId, response.response());
        log.info("Player career teams sync: playerId={} — {} takim yazildi",
                playerId, written);
        return written;
    }
}

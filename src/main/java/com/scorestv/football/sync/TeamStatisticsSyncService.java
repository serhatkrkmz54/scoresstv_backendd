package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.TeamStatisticsApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Bir takimin bir lig+sezondaki istatistiklerini API-Football'dan ceker ve
 * JSONB olarak DB'ye yazar. Cagri:
 *   {@code GET /teams/statistics?team=X&league=Y&season=Z}
 *
 * <p>Yanit obje (liste degil) — {@link TeamStatisticsApiDto} tum
 * alanlari Map'e toplar.
 */
@Service
public class TeamStatisticsSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeamStatisticsSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<TeamStatisticsApiDto>>
            STATS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final TeamStatisticsUpserter upserter;

    public TeamStatisticsSyncService(ApiFootballClient client,
                                     TeamStatisticsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public int sync(Long teamId, Long leagueId, Integer season) {
        ApiFootballResponse<TeamStatisticsApiDto> response = client.get(
                "/teams/statistics",
                Map.of("team", teamId, "league", leagueId, "season", season),
                STATS_TYPE);
        TeamStatisticsApiDto dto = response.response();
        if (dto == null || dto.getProperties().isEmpty()) {
            return 0;
        }
        int written = upserter.upsert(teamId, leagueId, season, dto.getProperties());
        if (written > 0) {
            log.info("Takim istatistik sync: teamId={} leagueId={} season={} — OK",
                    teamId, leagueId, season);
        }
        return written;
    }
}

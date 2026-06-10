package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import com.scorestv.football.sync.dto.TopPlayerApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir lig+sezon icin top scorers / assists / cards listelerini API-Football'dan
 * cekip DB'ye senkronlar.
 *
 * <p>Endpoint'ler ayni yapida doner; tek metod ({@link #sync(Long, Integer,
 * Category)}) kategori parametresiyle dogru API endpoint'ini secer.
 */
@Service
public class TopPlayersSyncService {

    private static final Logger log = LoggerFactory.getLogger(TopPlayersSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TopPlayerApiDto>>>
            TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final TopPlayersUpserter upserter;

    public TopPlayersSyncService(ApiFootballClient client, TopPlayersUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    /**
     * Bir kategoriyi cekip DB'ye replace eder. Coverage kontrolu cagirana ait:
     * kapsam yoksa zaten bos doner, ama yine de API'yi zorlamamak icin
     * cagirma stratejisi yukarida (lazy sync) coverage bayraklarina bakar.
     */
    public TopPlayersSyncResult sync(Long leagueId, Integer season, Category category) {
        String path = switch (category) {
            case SCORERS -> "/players/topscorers";
            case ASSISTS -> "/players/topassists";
            case YELLOW_CARDS -> "/players/topyellowcards";
            case RED_CARDS -> "/players/topredcards";
        };
        ApiFootballResponse<List<TopPlayerApiDto>> response = client.get(
                path,
                Map.of("league", leagueId, "season", season),
                TYPE);
        List<TopPlayerApiDto> items = response.response();
        int written = upserter.replace(leagueId, season, category,
                items == null ? List.of() : items);
        if (written > 0) {
            log.info("Top {} sync: leagueId={} season={} — {} satir",
                    category.name().toLowerCase(), leagueId, season, written);
        }
        return new TopPlayersSyncResult(leagueId, season, category, written);
    }
}

package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.PlayerSeasonApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir takimin verilen sezondaki TUM oyuncularinin aggregated istatistiklerini
 * API'den ceker:
 *   {@code GET /players?team=X&season=Y&page=N}
 *
 * <p>API yaniti sayfali — {@code paging.current/total} alanlarina bakip
 * tum sayfalar bitene kadar dongu. ~25 kisilik kadro icin 1-2 sayfa yeter
 * (sayfa basina 20 oyuncu).
 *
 * <p>REPLACE pattern: ilk sayfa oncesinde takim+sezonun TUM eski satirlari
 * silinir, sonra her sayfa append edilir. Sayfa ortasinda hata olursa
 * eski veri kaybolur ama empty-debounce ile bir sonraki istekte tekrar
 * denenir (UI cogu zaman cache'ten okur, kullanici farketmez).
 *
 * <p>Rate limit {@link ApiFootballClient} icinde dakika basina throttle'lanir.
 */
@Service
public class PlayerSeasonStatsSyncService {

    private static final Logger log = LoggerFactory.getLogger(PlayerSeasonStatsSyncService.class);

    /** Sayfa basina maksimum cagri sayisi — sonsuz dongu korumasi. */
    private static final int MAX_PAGES = 10;

    private static final ParameterizedTypeReference<ApiFootballResponse<List<PlayerSeasonApiDto>>>
            PLAYERS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final PlayerSeasonStatsUpserter upserter;

    public PlayerSeasonStatsSyncService(ApiFootballClient client,
                                        PlayerSeasonStatsUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    /**
     * Bir takim+sezon icin tum sayfalari sirayla ceker, REPLACE pattern
     * ile DB'ye yazar.
     *
     * @return yazilan toplam satir sayisi (player × league × season)
     */
    public int sync(Long teamId, Integer season) {
        if (teamId == null || season == null) return 0;
        upserter.replaceStart(teamId, season);
        int totalWritten = 0;
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages && page <= MAX_PAGES) {
            Map<String, Object> params = new HashMap<>();
            params.put("team", teamId);
            params.put("season", season);
            params.put("page", page);
            ApiFootballResponse<List<PlayerSeasonApiDto>> response =
                    client.get("/players", params, PLAYERS_TYPE);
            if (response == null) break;
            List<PlayerSeasonApiDto> items = response.response();
            if (items != null && !items.isEmpty()) {
                totalWritten += upserter.upsertPage(teamId, season, items);
            }
            // Paging cikar — null gelirse 1 sayfa kabul et
            if (response.paging() != null) {
                totalPages = response.paging().total();
            }
            page++;
        }
        log.info("PlayerSeasonStats sync: teamId={} season={} — {} sayfa, {} kayit",
                teamId, season, Math.min(totalPages, MAX_PAGES), totalWritten);
        return totalWritten;
    }
}

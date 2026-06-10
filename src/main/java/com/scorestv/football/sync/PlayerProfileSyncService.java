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
 * Tek bir oyuncunun belirli sezondaki profil + tum istatistik (lig basina ayri)
 * verilerini API'den ceker:
 *   {@code GET /players?id=X&season=Y&page=N}
 *
 * <p>Yanit:
 * <ul>
 *   <li>player objesi (id/name/firstname/lastname/age/birth/height/weight/photo)</li>
 *   <li>statistics[] — oyuncunun o sezonda oynadigi her turnuva icin ayri obje
 *       (lig + games + goals + passes + tackles + duels + dribbles + fouls +
 *       cards + penalty + substitutes)</li>
 * </ul>
 *
 * <p>Genelde sayfa sayisi 1; tek oyuncu icin nadiren 1 sayfayi gecer. Yine de
 * sayfali paging dongusu kullaniyoruz.
 *
 * <p>Yan etkiler:
 * <ul>
 *   <li>{@link PlayerProfileUpserter} ile master tabloya TAM profil yazilir</li>
 *   <li>{@link PlayerSeasonStatsUpserter} ile (player, team, league, season)
 *       satirlari REPLACE pattern ile yazilir — varolan team+season kayitlari
 *       silinmez (cunku replaceStart team+season bazli; biz tek oyuncu icin
 *       hassas silme yapamiyoruz, sadece save). Bu yuzden burada ozel
 *       upserter mantigi: dup'i pre-check ile atla.</li>
 * </ul>
 */
@Service
public class PlayerProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(PlayerProfileSyncService.class);

    private static final int MAX_PAGES = 5;

    private static final ParameterizedTypeReference<ApiFootballResponse<List<PlayerSeasonApiDto>>>
            PLAYERS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final PlayerProfileUpserter profileUpserter;
    private final PlayerSeasonStatsUpserter statsUpserter;

    public PlayerProfileSyncService(ApiFootballClient client,
                                    PlayerProfileUpserter profileUpserter,
                                    PlayerSeasonStatsUpserter statsUpserter) {
        this.client = client;
        this.profileUpserter = profileUpserter;
        this.statsUpserter = statsUpserter;
    }

    /**
     * Tek bir oyuncunun belirli sezonu icin tum verisini sync eder.
     *
     * @return yazilan PlayerSeasonStat satir sayisi (player×team×league)
     */
    public int sync(Long playerId, Integer season) {
        if (playerId == null || season == null) return 0;
        int totalWritten = 0;
        int page = 1;
        int totalPages = 1;
        boolean profileWritten = false;
        while (page <= totalPages && page <= MAX_PAGES) {
            Map<String, Object> params = new HashMap<>();
            params.put("id", playerId);
            params.put("season", season);
            params.put("page", page);
            ApiFootballResponse<List<PlayerSeasonApiDto>> response =
                    client.get("/players", params, PLAYERS_TYPE);
            if (response == null) break;
            List<PlayerSeasonApiDto> items = response.response();
            if (items != null && !items.isEmpty()) {
                for (PlayerSeasonApiDto dto : items) {
                    // 1) Player master full profile (bir kez)
                    if (!profileWritten && dto.player() != null) {
                        profileUpserter.upsert(dto.player());
                        profileWritten = true;
                    }
                    // 2) Sezon istatistikleri — her statistics[] icin (player, team,
                    // league, season) satiri yazilir. PlayerSeasonStatsUpserter'in
                    // upsertPage'i team+season filtresi kullanir (queriedTeamId);
                    // burada teamId yok (id bazli sorgu), tum entry'leri yazariz.
                    if (dto.statistics() != null) {
                        for (PlayerSeasonApiDto.StatisticsEntry entry : dto.statistics()) {
                            if (entry == null) continue;
                            int n = statsUpserter.upsertSingleEntry(playerId, entry);
                            totalWritten += n;
                        }
                    }
                }
            }
            if (response.paging() != null) totalPages = response.paging().total();
            page++;
        }
        log.info("Player profile sync: playerId={} season={} — {} stat satiri yazildi",
                playerId, season, totalWritten);
        return totalWritten;
    }
}

package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.SidelinedApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Oyuncu sakatlik/cezalik senkronu.
 *   {@code GET /sidelined?player=X}
 *
 * <p>Takim sayfasi icin: bir takim kadrosundaki tum oyunculari sirayla
 * gecip her birinin sidelined kaydini tazeler.
 *
 * <p>API yaniti player_id icermedigi icin (sorgu parametresinden bilinir),
 * batch {@code ?players=A-B-C} cagrisinin sonucunu hangi oyuncuya
 * baglayacagimiz belirsiz — bu yuzden per-player cagri yapiyoruz.
 */
@Service
public class SidelinedSyncService {

    private static final Logger log = LoggerFactory.getLogger(SidelinedSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<SidelinedApiDto>>>
            SIDELINED_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final SidelinedUpserter upserter;

    public SidelinedSyncService(ApiFootballClient client, SidelinedUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    /** Tek bir oyuncu icin. */
    public int syncOne(Long playerId) {
        ApiFootballResponse<List<SidelinedApiDto>> response = client.get(
                "/sidelined", Map.of("player", playerId), SIDELINED_TYPE);
        return upserter.upsert(playerId, response.response());
    }

    /**
     * Birden cok oyuncu icin sirayla. Takim kadro sayfasi icin uygundur —
     * SyncRateLimiter bunlari rate limit altinda sirayla cagirir.
     */
    public int syncForPlayers(Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) return 0;
        int total = 0;
        for (Long playerId : playerIds) {
            if (playerId == null) continue;
            try {
                total += syncOne(playerId);
            } catch (RuntimeException ex) {
                log.warn("Sidelined sync hatasi (playerId={}): {}", playerId, ex.getMessage());
            }
        }
        log.info("Sidelined sync — {} oyuncu icin toplam {} kayit yazildi",
                playerIds.size(), total);
        return total;
    }
}

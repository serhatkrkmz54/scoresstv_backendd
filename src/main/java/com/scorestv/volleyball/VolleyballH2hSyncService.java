package com.scorestv.volleyball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Iki takim arasindaki gecmis maclari (H2H) senkronlar.
 *
 * <p>API ucu: {@code /games/h2h?h2h=A-B}. Yanit yapisi {@code /games} ile
 * ayni — {@link VbGameDto} listesi doner. Maclari DB'ye yazmak icin
 * {@link VolleyballSyncService#upsertAll(List, boolean)} delege edilir
 * ({@code notify=false} — H2H gecmis verisidir).
 */
@Service
public class VolleyballH2hSyncService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballH2hSyncService.class);

    private final VolleyballApiClient client;
    private final VolleyballSyncService gameSyncService;

    public VolleyballH2hSyncService(VolleyballApiClient client,
                                    VolleyballSyncService gameSyncService) {
        this.client = client;
        this.gameSyncService = gameSyncService;
    }

    /**
     * Iki takim icin H2H maclari ceker ve DB'ye yazar.
     *
     * @return yazilan mac sayisi (mevcut+yeni toplam)
     */
    public int sync(long team1Id, long team2Id) {
        List<VbGameDto> games;
        try {
            games = client.fetchH2h(team1Id, team2Id);
        } catch (Exception e) {
            log.warn("Voleybol H2H API hatasi {}-{}: {}", team1Id, team2Id, e.toString());
            return 0;
        }
        if (games.isEmpty()) return 0;
        return gameSyncService.upsertAll(games, false);
    }
}

package com.scorestv.basketball;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Iki takim arasindaki gecmis maclari (H2H) senkronlar.
 *
 * <p>API ucu: {@code /games/h2h?h2h=A-B}. Yanit yapisi {@code /games} ile
 * ayni — {@link BkGameDto} listesi doner. Maclari DB'ye yazmak icin
 * {@link BasketballSyncService#upsertAll(List, boolean)} delege edilir
 * ({@code notify=false} — H2H gecmis verisidir, bildirim uretmemeli).
 */
@Service
public class BasketballH2hSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballH2hSyncService.class);

    private final BasketballApiClient client;
    private final BasketballSyncService gameSyncService;

    public BasketballH2hSyncService(BasketballApiClient client,
                                    BasketballSyncService gameSyncService) {
        this.client = client;
        this.gameSyncService = gameSyncService;
    }

    /**
     * Iki takim icin H2H maclari ceker ve DB'ye yazar.
     *
     * @return yazilan mac sayisi (mevcut+yeni toplam)
     */
    public int sync(long team1Id, long team2Id) {
        List<BkGameDto> games;
        try {
            games = client.fetchH2h(team1Id, team2Id);
        } catch (Exception e) {
            log.warn("Basketbol H2H API hatasi {}-{}: {}",
                    team1Id, team2Id, e.toString());
            return 0;
        }
        int n = gameSyncService.upsertAll(games, false);
        log.info("Basketbol H2H sync {}-{} -> {} mac yazildi", team1Id, team2Id, n);
        return n;
    }
}

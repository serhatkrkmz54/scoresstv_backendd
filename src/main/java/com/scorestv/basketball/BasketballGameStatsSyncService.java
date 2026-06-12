package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballGame;
import com.scorestv.basketball.domain.BasketballGameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bir mac icin takim + oyuncu istatistiklerini senkronlar.
 *
 * <p>Iki API cagrisi paralel mantikli ama burada seri yapilir — alt-katman
 * client'in {@code throttle()} mekanizmasi olas: paralelle istek kazalari
 * olmasin. Lazy sync orchestrator (B-Faz4) ihtiyac duyarsa CompletableFuture
 * ile parallelize edebilir.
 *
 * <p>Hata yonetimi: bir endpoint patlar diger devam — kismi veri yine de
 * gosterilebilir (futbol lazy-sync paterni).
 */
@Service
public class BasketballGameStatsSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballGameStatsSyncService.class);

    private final BasketballApiClient client;
    private final BasketballGameRepository gameRepo;
    private final BasketballGameTeamStatsUpserter teamStatsUpserter;
    private final BasketballGamePlayerStatsUpserter playerStatsUpserter;

    public BasketballGameStatsSyncService(BasketballApiClient client,
                                           BasketballGameRepository gameRepo,
                                           BasketballGameTeamStatsUpserter teamStatsUpserter,
                                           BasketballGamePlayerStatsUpserter playerStatsUpserter) {
        this.client = client;
        this.gameRepo = gameRepo;
        this.teamStatsUpserter = teamStatsUpserter;
        this.playerStatsUpserter = playerStatsUpserter;
    }

    /** Takim istatistiklerini ceker ve replace eder. */
    @Transactional
    public int syncTeamStats(long gameId) {
        BasketballGame game = gameRepo.findById(gameId).orElse(null);
        if (game == null) {
            log.warn("Team stats sync atlandi — game DB'de yok: id={}", gameId);
            return 0;
        }
        List<BkGameTeamStatDto> dtos;
        try {
            dtos = client.fetchGameTeamStats(gameId);
        } catch (Exception e) {
            log.warn("Game team stats API hatasi id={}: {}", gameId, e.toString());
            return 0;
        }
        int n = teamStatsUpserter.replaceAll(game, dtos);
        log.debug("Basketbol team stats sync id={} -> {} satir", gameId, n);
        return n;
    }

    /** Oyuncu istatistiklerini ceker ve replace eder. */
    @Transactional
    public int syncPlayerStats(long gameId) {
        BasketballGame game = gameRepo.findById(gameId).orElse(null);
        if (game == null) {
            log.warn("Player stats sync atlandi — game DB'de yok: id={}", gameId);
            return 0;
        }
        List<BkGamePlayerStatDto> dtos;
        try {
            dtos = client.fetchGamePlayerStats(gameId);
        } catch (Exception e) {
            log.warn("Game player stats API hatasi id={}: {}", gameId, e.toString());
            return 0;
        }
        int n = playerStatsUpserter.replaceAll(game, dtos);
        log.debug("Basketbol player stats sync id={} -> {} satir", gameId, n);
        return n;
    }

    /** Convenience — ikisini birlikte calistirir, toplam satir doner. */
    @Transactional
    public int syncBoth(long gameId) {
        return syncTeamStats(gameId) + syncPlayerStats(gameId);
    }
}

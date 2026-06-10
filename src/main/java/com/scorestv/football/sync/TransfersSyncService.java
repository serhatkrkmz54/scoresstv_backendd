package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.sync.dto.TransferApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bir takimin transferlerini API-Football'dan ceker:
 *   {@code GET /transfers?team=X}
 *
 * <p>Yanit her oyuncunun TUM kariyer transferlerini ic ice doner; biz
 * flat olarak DB'ye yaziyoruz (takim filtresi DB sorgusunda yapilir).
 */
@Service
public class TransfersSyncService {

    private static final Logger log = LoggerFactory.getLogger(TransfersSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TransferApiDto>>>
            TRANSFERS_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final TransfersUpserter upserter;

    public TransfersSyncService(ApiFootballClient client,
                                TransfersUpserter upserter) {
        this.client = client;
        this.upserter = upserter;
    }

    public int syncByTeam(Long teamId) {
        ApiFootballResponse<List<TransferApiDto>> response = client.get(
                "/transfers", Map.of("team", teamId), TRANSFERS_TYPE);
        List<TransferApiDto> items = response.response();
        int written = upserter.upsert(items);
        log.info("Transfers sync: teamId={} — {} satir yazildi", teamId, written);
        return written;
    }

    /**
     * Bir oyuncunun TUM kariyer transferleri:
     *   {@code GET /transfers?player=X}
     *
     * <p>Yanit format: yine "player + transfers[]" yapisinda — tek oyuncu icin
     * 1 element. Player detay sayfasi acildiginda lazy sync ile cagrilir.
     */
    public int syncByPlayer(Long playerId) {
        ApiFootballResponse<List<TransferApiDto>> response = client.get(
                "/transfers", Map.of("player", playerId), TRANSFERS_TYPE);
        List<TransferApiDto> items = response.response();
        int written = upserter.upsert(items);
        log.info("Transfers sync: playerId={} — {} satir yazildi", playerId, written);
        return written;
    }
}

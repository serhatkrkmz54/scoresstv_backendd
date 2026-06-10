package com.scorestv.football.sync;

/**
 * İki takım arasındaki H2H senkron sonucu.
 *
 * @param teamA              Birinci takım id
 * @param teamB              İkinci takım id
 * @param fixturesUpserted   API'den çekilip DB'ye eklenen/güncellenen maç sayısı
 */
public record H2hSyncResult(Long teamA, Long teamB, int fixturesUpserted) {
}

package com.scorestv.football.sync;

/**
 * Takım senkronunun özeti.
 *
 * @param leaguesProcessed başarıyla işlenen lig sayısı
 * @param leaguesFailed    başarısız olan lig sayısı
 * @param teamsUpserted    upsert edilen toplam takım sayısı
 */
public record TeamSyncResult(
        int leaguesProcessed,
        int leaguesFailed,
        int teamsUpserted
) {
}

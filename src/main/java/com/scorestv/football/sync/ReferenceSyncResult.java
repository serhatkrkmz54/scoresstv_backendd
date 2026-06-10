package com.scorestv.football.sync;

/**
 * Referans veri senkronunun (ülkeler + ligler + sezonlar) özeti.
 *
 * @param countriesUpserted upsert edilen ülke sayısı
 * @param leaguesUpserted   upsert edilen lig sayısı
 * @param leaguesFailed     upsert edilemeyen lig sayısı
 * @param seasonsUpserted   upsert edilen toplam sezon sayısı
 */
public record ReferenceSyncResult(
        int countriesUpserted,
        int leaguesUpserted,
        int leaguesFailed,
        int seasonsUpserted
) {
}

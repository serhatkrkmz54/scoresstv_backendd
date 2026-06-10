package com.scorestv.football.sync;

/**
 * Fikstür penceresi senkronunun özeti.
 *
 * @param datesSucceeded   başarıyla senkronlanan tarih sayısı
 * @param datesFailed      başarısız olan tarih sayısı
 * @param fixturesUpserted DB'ye upsert edilen toplam maç sayısı
 */
public record FixtureSyncResult(
        int datesSucceeded,
        int datesFailed,
        int fixturesUpserted
) {
}

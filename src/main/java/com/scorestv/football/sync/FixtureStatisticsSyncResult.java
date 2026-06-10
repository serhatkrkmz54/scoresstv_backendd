package com.scorestv.football.sync;

/**
 * Bir maç için istatistik senkron sonucu.
 *
 * @param fixtureId    maç id'si
 * @param statsWritten yazılan toplam istatistik satırı sayısı (2 takım × ~16 stat)
 */
public record FixtureStatisticsSyncResult(Long fixtureId, int statsWritten) {
}

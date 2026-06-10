package com.scorestv.football.sync;

/**
 * Bir maç için kadro senkron sonucu.
 *
 * @param fixtureId      maç id'si
 * @param lineupsWritten yazılan/güncellenen takım kadrosu sayısı (genelde 0 ya da 2)
 */
public record FixtureLineupsSyncResult(Long fixtureId, int lineupsWritten) {
}

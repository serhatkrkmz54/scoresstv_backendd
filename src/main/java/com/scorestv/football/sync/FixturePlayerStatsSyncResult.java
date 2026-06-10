package com.scorestv.football.sync;

/**
 * Bir maç için oyuncu istatistikleri senkron sonucu.
 *
 * @param fixtureId     maç id'si
 * @param playersWritten yazılan oyuncu satırı sayısı (her iki takım, ~28-40 oyuncu)
 */
public record FixturePlayerStatsSyncResult(Long fixtureId, int playersWritten) {
}

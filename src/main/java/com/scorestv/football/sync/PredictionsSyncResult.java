package com.scorestv.football.sync;

/**
 * Bir maç için tahmin senkron sonucu.
 *
 * @param fixtureId maç id'si
 * @param written   yazılan/güncellenen tahmin sayısı (0 ya da 1)
 */
public record PredictionsSyncResult(Long fixtureId, int written) {
}

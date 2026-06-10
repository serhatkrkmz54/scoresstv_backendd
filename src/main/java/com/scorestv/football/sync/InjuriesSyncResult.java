package com.scorestv.football.sync;

/**
 * Bir maç için sakatlık senkron sonucu.
 *
 * @param fixtureId       maç id'si
 * @param injuriesWritten yazılan injury satırı sayısı
 */
public record InjuriesSyncResult(Long fixtureId, int injuriesWritten) {
}

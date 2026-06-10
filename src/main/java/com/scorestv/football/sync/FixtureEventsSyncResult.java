package com.scorestv.football.sync;

/**
 * Bir fixture'ın olaylarının senkron sonucu.
 *
 * @param fixtureId  senkronlanan maç id'si
 * @param eventCount DB'ye yazılan toplam olay sayısı (eski olaylar silinir,
 *                   yeni gelenler tam set olarak yazılır)
 */
public record FixtureEventsSyncResult(Long fixtureId, int eventCount) {
}

package com.scorestv.football.live;

/**
 * Tek bir canlı tick çağrısının sonucu.
 *
 * @param fetched   API'den dönen canlı maç sayısı
 * @param broadcast değişimi tespit edilip WebSocket'e yayılan maç sayısı
 */
public record LiveTickerResult(int fetched, int broadcast) {
}

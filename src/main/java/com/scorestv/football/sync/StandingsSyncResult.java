package com.scorestv.football.sync;

/**
 * Bir lig + sezon için puan durumu senkron sonucu.
 *
 * @param leagueId      lig id
 * @param season        sezon yılı
 * @param rowsWritten   yazılan toplam puan durumu satırı sayısı (tüm gruplar)
 */
public record StandingsSyncResult(Long leagueId, Integer season, int rowsWritten) {
}

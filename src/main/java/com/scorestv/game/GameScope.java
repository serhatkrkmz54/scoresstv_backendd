package com.scorestv.game;

/**
 * Yarışma dönemi.
 *
 * <p>{@code MATCHDAY}: tek tur / maç günü yarışması — ör. "Şampiyonlar Ligi
 * 3. Eleme Turu" gibi belirli bir günün/turun maçları. Pencere (start-end)
 * o günü kapsar; lig bağı {@code leagueId} ile kurulur. Sezonluk lig
 * yarışması için {@code SEASON} + {@code leagueId} kullanılır.
 */
public enum GameScope {
    MATCHDAY, WEEKLY, MONTHLY, SEASON
}

package com.scorestv.game;

/** Yarışma sıralaması projeksiyonu (kullanıcı başına toplam coin + doğru sayısı). */
public interface CompetitionLeaderboardRow {
    Long getUserId();
    Long getCoins();
    Long getCorrectCount();
    Long getTotal();
}

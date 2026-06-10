package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Bir maçtaki tek bir oyuncunun performans istatistiği — maç detay yanıtı için.
 *
 * <p>API'nın nested yapısını korur (shots/goals/passes/...) — frontend için
 * tanıdık şekil. {@code rating} ve {@code passes.accuracy} String'tir (API
 * öyle gönderir).
 *
 * <p>{@code games} bloğu top-level alanlara (minutes, number, position,
 * rating, captain, substitute) yatırıldı — frontend daha rahat erişir.
 */
public record PlayerStatView(
        Long playerId,
        String playerName,
        String photo,
        Integer minutes,
        Integer number,
        String position,
        String rating,
        Boolean captain,
        Boolean substitute,
        Integer offsides,
        Shots shots,
        Goals goals,
        Passes passes,
        Tackles tackles,
        Duels duels,
        Dribbles dribbles,
        Fouls fouls,
        Cards cards,
        Penalty penalty
) implements Serializable {

    public record Shots(Integer total, Integer on) implements Serializable {}

    public record Goals(Integer total, Integer conceded, Integer assists, Integer saves)
            implements Serializable {}

    public record Passes(Integer total, Integer key, String accuracy) implements Serializable {}

    public record Tackles(Integer total, Integer blocks, Integer interceptions)
            implements Serializable {}

    public record Duels(Integer total, Integer won) implements Serializable {}

    public record Dribbles(Integer attempts, Integer success, Integer past)
            implements Serializable {}

    public record Fouls(Integer drawn, Integer committed) implements Serializable {}

    public record Cards(Integer yellow, Integer red) implements Serializable {}

    /** Yanıt tarafında doğru yazımla ("committed") — API'daki typo'dan farklı. */
    public record Penalty(
            Integer won,
            Integer committed,
            Integer scored,
            Integer missed,
            Integer saved
    ) implements Serializable {}
}

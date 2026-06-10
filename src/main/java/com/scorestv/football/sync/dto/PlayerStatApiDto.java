package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /fixtures/players} yanıtındaki tek bir takım bloğu.
 * Yanıt iki öğe içerir (ev + deplasman); her öğenin {@code players[]} listesi
 * o maçta oynayan ~14-20 oyuncuyu taşır.
 *
 * <p>Her oyuncunun {@code statistics} alanı array'dir ama API'da daima 1
 * elemanlıdır — upserter ilk (ve tek) elemanı kullanır.
 *
 * <p>{@link Penalty#commited()} alanı API'da typo ile ("commited") gelir;
 * record adı buna uydurulur, DB'ye {@code penalty_committed} doğru yazımla
 * yazılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatApiDto(
        Team team,
        List<PlayerEntry> players
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerEntry(Player player, List<Statistics> statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(Long id, String name, String photo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Statistics(
            Games games,
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
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Games(
            Integer minutes,
            Integer number,
            String position,
            String rating,
            Boolean captain,
            Boolean substitute
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shots(Integer total, Integer on) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(Integer total, Integer conceded, Integer assists, Integer saves) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Passes(Integer total, Integer key, String accuracy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tackles(Integer total, Integer blocks, Integer interceptions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Duels(Integer total, Integer won) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dribbles(Integer attempts, Integer success, Integer past) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fouls(Integer drawn, Integer committed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cards(Integer yellow, Integer red) {}

    /** API'daki typo'lu "commited" alanı için record adı bilinçli olarak böyle. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Penalty(
            Integer won,
            Integer commited,
            Integer scored,
            Integer missed,
            Integer saved
    ) {}
}

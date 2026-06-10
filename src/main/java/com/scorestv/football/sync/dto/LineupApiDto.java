package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * API-Football {@code /fixtures/lineups} yanıtındaki tek bir takım kadrosu.
 * Yanıt iki öğe içerir: ev sahibi ve deplasman.
 *
 * <p>Yapı: takım + (renkler) + formasyon + ilk 11 + yedekler + koç.
 *
 * <p>JSON'daki {@code startXI} alanını Java naming convention'ına uydurmak
 * için {@code @JsonProperty} ile {@code startXi}'ye eşlenir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineupApiDto(
        Team team,
        String formation,
        @JsonProperty("startXI") List<PlayerWrap> startXi,
        List<PlayerWrap> substitutes,
        Coach coach
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo, Colors colors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Colors(ColorSet player, ColorSet goalkeeper) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ColorSet(String primary, String number, String border) {}

    /** API her oyuncuyu {"player": {...}} sarmalıyla verir. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerWrap(Player player) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(
            Long id,
            String name,
            Integer number,
            String pos,
            /** "X:Y" — saha üzerindeki konum. Yedekler için null. */
            String grid
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coach(Long id, String name, String photo) {}
}

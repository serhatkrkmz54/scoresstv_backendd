package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Basketball {@code /leagues} yanit ogesi: lig + ulke + sezonlar listesi.
 *
 * <p>Her sezon icin start/end tarih + coverage flag'leri var. Sezon dropdown'i
 * bunlari okur, season picker'da hangi sezonlarda games/standings/players
 * verisi olduguna gore disable/enable yapilabilir.
 *
 * <p>{@code seasons[].current = true} olan, ligin guncel sezonudur. Birden
 * fazla "current" olabilirse en sonuncusu alinir (defansif).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkLeagueDto(
        Long id,
        String name,
        String type,
        String logo,
        Country country,
        List<Season> seasons
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(Long id, String name, String code, String flag) {}

    /**
     * Sezon kaydi — kapsama bilgileri ile birlikte.
     *
     * <p>{@code start}/{@code end}: "YYYY-MM-DD" string olarak gelir (API'den
     * gelen ham hali korunur; parse'i caller yapar).
     *
     * <p>{@code coverage}: hangi tip verinin bu sezonda var oldugunu gosterir.
     * Null guvenli — eski sezonlarda coverage objesi tamamen eksik olabilir.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(
            String season,
            String start,
            String end,
            Boolean current,
            Coverage coverage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coverage(
            GamesCoverage games,
            Boolean standings,
            Boolean players,
            Boolean odds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamesCoverage(GamesStatistics statistics) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamesStatistics(Boolean teams, Boolean players) {}
}

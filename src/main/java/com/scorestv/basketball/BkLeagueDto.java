package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Basketball {@code /leagues} yanıt öğesi: lig + ülke + sezonlar.
 * {@code seasons[].current=true} olan, ligin güncel sezonudur.
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(String season, Boolean current) {}
}

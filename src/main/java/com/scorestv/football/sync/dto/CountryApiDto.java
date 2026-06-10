package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Football {@code /countries} yanıtındaki tek bir ülke.
 *
 * @param name ülke adı (örn. "Turkey")
 * @param code ISO kodu (örn. "TR"); uluslararası kayıtlarda null olabilir
 * @param flag bayrak görseli URL'si
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountryApiDto(
        String name,
        String code,
        String flag
) {
}

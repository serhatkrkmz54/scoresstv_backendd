package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** API-Basketball {@code /countries} yanıt öğesi. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkCountryDto(Long id, String name, String code, String flag) {}

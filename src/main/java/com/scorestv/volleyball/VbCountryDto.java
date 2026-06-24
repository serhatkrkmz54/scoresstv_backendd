package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** API-Volleyball {@code /countries} yanit ogesi. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbCountryDto(Long id, String name, String code, String flag) {}

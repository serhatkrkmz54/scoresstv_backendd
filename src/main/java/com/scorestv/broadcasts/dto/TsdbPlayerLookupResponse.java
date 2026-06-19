package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code lookupplayer.php} kök yanıtı: {@code {"players": [...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbPlayerLookupResponse(List<TsdbPlayerDto> players) {}

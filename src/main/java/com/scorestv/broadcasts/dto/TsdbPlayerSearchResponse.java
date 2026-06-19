package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code searchplayers.php} kök yanıtı: {@code {"player": [...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbPlayerSearchResponse(List<TsdbPlayerDto> player) {}

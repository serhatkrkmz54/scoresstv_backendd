package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code lookuptv.php} kök yanıtı: {@code {"tvevent": [...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbTvResponse(List<TsdbTvDto> tvevent) {}

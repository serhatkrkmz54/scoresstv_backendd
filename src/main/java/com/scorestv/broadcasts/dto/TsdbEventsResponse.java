package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code searchevents.php} kök yanıtı: {@code {"event": [...]}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbEventsResponse(List<TsdbEventDto> event) {}

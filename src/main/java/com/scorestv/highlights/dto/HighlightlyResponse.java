package com.scorestv.highlights.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Highlightly {@code /highlights} sarmalayıcı yanıtı (yalnız {@code data}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HighlightlyResponse(List<HighlightlyHighlightDto> data) {}

package com.scorestv.highlights.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Highlightly {@code /highlights/geo-restrictions/{id}} yanıtı (ücretli plan).
 * {@code embeddable} = embedUrl gömülebilir mi; {@code blockedCountries} =
 * gömme/izlemenin engellendiği ülke kodları.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HighlightlyGeoRestrictionDto(
        String state,
        Boolean embeddable,
        List<String> allowedCountries,
        List<String> blockedCountries
) {}

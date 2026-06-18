package com.scorestv.broadcasts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TheSportsDB {@code lookuptv.php} yanıtındaki tek TV yayını kaydı
 * (yalnız kullandığımız alanlar).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TsdbTvDto(
        String idChannel,
        String strChannel,
        String strCountry,
        String strLogo
) {}

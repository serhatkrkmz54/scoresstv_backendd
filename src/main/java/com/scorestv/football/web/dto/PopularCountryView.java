package com.scorestv.football.web.dto;

import java.io.Serializable;

/** Sol ray "Ülkeler" listesi öğesi. ID config'te (popular-country-ids). */
public record PopularCountryView(
        Long id,
        String name,
        String slug,
        String flag,
        String code
) implements Serializable {
}

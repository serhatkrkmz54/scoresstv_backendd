package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * API-Football {@code /teams/statistics} yaniti — passthrough.
 *
 * <p>Cok zengin yapi (form, fixtures, goals, biggest, clean_sheet,
 * failed_to_score, penalty, lineups, cards). Predictions teams_json'da
 * uyguladigimiz desenle JSONB icin tum yaniti Map'e parse ediyoruz.
 * Frontend dogrudan kullanir.
 *
 * <p>JsonAnyGetter/Setter ile Jackson tum alanlari (league/team/form/...)
 * tek Map'e toplar. League ve team alanlari ayrica DB ile referans icin
 * sorgu parametrelerinden bilindiginden bu DTO'da ayri tutmuyoruz.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamStatisticsApiDto {

    private final Map<String, Object> properties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonAnySetter
    public void set(String key, Object value) {
        properties.put(key, value);
    }
}

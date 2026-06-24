package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Volleyball {@code /leagues} yanit ogesi: lig + ulke + sezonlar listesi.
 *
 * <p>API ornek:
 * <pre>{
 *   "id": 1, "name": "...", "type": "League"/"Cup", "logo": "...",
 *   "country": {"id":..,"name":..,"code":..,"flag":..},
 *   "seasons": [{"season": 2024, "current": true, "start": "...", "end": "..."}]
 * }</pre>
 *
 * <p>Voleybol sezonu API'de cogunlukla integer ({@code 2024}) doner; basketbol
 * "2024-2025" string formatindan farkli — bu yuzden {@code season} alani
 * defansif {@link Object} (Integer veya String tolere edilir).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbLeagueDto(
        Long id,
        String name,
        String type,
        String logo,
        Country country,
        List<Season> seasons
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(Long id, String name, String code, String flag) {}

    /**
     * Sezon kaydi. {@code season} Integer veya String olabilir — defansif
     * {@link Object}; caller {@code String.valueOf} ile normalize eder.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(
            Object season,
            Boolean current,
            String start,
            String end
    ) {
        /** Sezonu her zaman string olarak doner ("2024" gibi). */
        public String seasonAsString() {
            return season == null ? null : String.valueOf(season);
        }
    }
}

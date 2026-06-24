package com.scorestv.volleyball.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Voleybol lig hub'i — basketbol {@code BasketballLeagueHubResponse}'in
 * voleybol esi. Ulke bazinda voleybol liglerini gruplar; mobile onboarding
 * accordion'unda favori takim secimi icin.
 */
public record VolleyballLeagueHubResponse(
        int totalLeagues,
        List<CountryGroup> countries
) implements Serializable {

    public record CountryGroup(
            String name,
            String code,
            String flag,
            List<LeagueRef> leagues
    ) implements Serializable {}

    public record LeagueRef(
            Long id,
            String name,
            String logo,
            /** API'den gelen tip ("League" / "Cup"). */
            String type,
            /** Guncel sezon — orn "2024". */
            String currentSeason
    ) implements Serializable {}
}

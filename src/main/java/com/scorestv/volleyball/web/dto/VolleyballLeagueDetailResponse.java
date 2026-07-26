package com.scorestv.volleyball.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Voleybol lig detay sayfasi aggregate cevabi — web SSR tek istekle sayfayi
 * doldurur. Basketbol {@code BasketballLeagueDetailResponse}'un LEANER esi
 * (voleybolda top-players / coverage verisi yok).
 *
 * <p>Standings gruplari {@link VolleyballStandingsPageResponse.Group} ile
 * birebir ayni sekil — web tarafinda tek tip yeter.
 */
public record VolleyballLeagueDetailResponse(
        Long id,
        String slug,
        String name,
        String type,
        String logo,
        Country country,
        String currentSeason,
        String selectedSeason,
        List<String> availableSeasons,
        List<VolleyballStandingsPageResponse.Group> standings,
        List<GameSummary> recentGames,
        List<GameSummary> upcomingGames,
        VolleyballLeagueSeoResponse seo) {

    public record Country(String name, String code, String flag) {}

    public record TeamRef(Long id, String name, String logo) {}

    /** Lig fikstur listesi satiri (son/yaklasan maclar). */
    public record GameSummary(
            Long id,
            String slug,
            Instant kickoff,
            String statusShort,
            String statusText,
            TeamRef home,
            TeamRef away,
            Integer homeSets,
            Integer awaySets,
            String week) {}

    /** Icerigi bos aggregate (cache'lememek icin kullanilabilir). */
    public boolean isThin() {
        return (standings == null || standings.isEmpty())
                && (recentGames == null || recentGames.isEmpty())
                && (upcomingGames == null || upcomingGames.isEmpty());
    }
}

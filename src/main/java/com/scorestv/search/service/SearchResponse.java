package com.scorestv.search.service;

import java.time.Instant;
import java.util.List;

/**
 * Public arama yaniti — tum tipler tek payload'da donulur.
 *
 * <p>Mobile/web tarafinda kullanici 3 harf yazip aramaya basladiginda
 * her tipten en uygun 5-10 sonucu gosteririz; tek istek + tek payload
 * ile bunu cozeyim diye <code>SearchHit</code> generic, <code>SearchResponse</code>
 * tip-bazli ayri listeler tutuyor.
 */
public record SearchResponse(
        String query,
        long tookMs,
        List<TeamHit> teams,
        List<LeagueHit> leagues,
        List<PlayerHit> players,
        List<FixtureHit> fixtures,
        List<CountryHit> countries,
        List<CoachHit> coaches
) {
    public record TeamHit(
            Long id, String name, String nameTr, String slug,
            String country, String countryTr, String logoUrl
    ) {}

    public record LeagueHit(
            Long id, String name, String nameTr, String slug,
            String country, String countryTr, String type,
            String logoUrl, String flagUrl
    ) {}

    public record PlayerHit(
            Long id, String name, String slug,
            String nationality, Integer age, String photoUrl
    ) {}

    public record FixtureHit(
            Long id, String slug, String matchup, String matchupTr,
            Long leagueId, String leagueName, String leagueNameTr,
            Instant kickoff, String statusShort
    ) {}

    public record CountryHit(
            Long id, String name, String nameTr, String slug,
            String code, String flagUrl
    ) {}

    /**
     * Koç sonucu. Koç detay sayfasi yok; frontend hafif bilgi karti gosterir
     * ve {@code currentTeamId} biliniyorsa "Takima git" ile takim sayfasina
     * yonlendirir. {@code currentTeamId}/{@code currentTeamName} null olabilir
     * (deep-search ile soğuk gelen koçta takim henuz bilinmiyor).
     */
    public record CoachHit(
            Long id, String name, String nationality, Integer age,
            String photoUrl, Long currentTeamId, String currentTeamName
    ) {}
}

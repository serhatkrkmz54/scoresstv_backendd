package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * API-Football {@code /leagues} yanıtındaki tek bir lig öğesi.
 *
 * <p>Her öğe ligin kendisini, ülkesini ve <b>tüm sezonlarını</b> gömülü taşır;
 * bu yüzden tek bir {@code /leagues} çağrısı hem ligleri hem sezonları getirir.
 * Sezon başına {@link Coverage} de döner — hangi verinin available olduğunu
 * gösterir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeagueApiDto(
        League league,
        Country country,
        List<Season> seasons
) {

    /** Ligin kendisi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record League(
            Long id,
            String name,
            /** "League" veya "Cup". */
            String type,
            String logo
    ) {}

    /** Ligin ülkesi. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(
            String name,
            String code,
            String flag
    ) {}

    /** Lige ait tek bir sezon (coverage dahil). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(
            Integer year,
            /** Sezon başlangıcı, ISO tarih "yyyy-MM-dd". */
            String start,
            /** Sezon bitişi, ISO tarih "yyyy-MM-dd". */
            String end,
            /** Bu, ligin geçerli sezonu mu? */
            Boolean current,
            Coverage coverage
    ) {}

    /**
     * Sezona özgü veri kapsam bayrakları. Sezon henüz başlamadıysa hepsi
     * false; başladıktan sonra API doldurur. UI'da "henüz veri yok" gösterimi
     * ve lazy sync'in "boş yere çağırma" kararı için kullanılır.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coverage(
            Fixtures fixtures,
            Boolean standings,
            Boolean players,
            @JsonProperty("top_scorers") Boolean topScorers,
            @JsonProperty("top_assists") Boolean topAssists,
            @JsonProperty("top_cards") Boolean topCards,
            Boolean injuries,
            Boolean predictions,
            Boolean odds
    ) {}

    /** Fikstür-seviyesindeki coverage alt-bayrakları. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fixtures(
            Boolean events,
            Boolean lineups,
            @JsonProperty("statistics_fixtures") Boolean statisticsFixtures,
            @JsonProperty("statistics_players") Boolean statisticsPlayers
    ) {}
}

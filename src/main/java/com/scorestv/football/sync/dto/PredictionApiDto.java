package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * API-Football {@code /predictions} yanıtındaki tek bir maç tahmini.
 *
 * <p>Sadece kullandığımız alt kümeleri ({@link Predictions}, {@link Comparison})
 * tutuyoruz; {@code teams.home/away} derin istatistikleri ve {@code h2h}
 * dizisi ihtiyaç olmadıkça parse edilmez (Jackson {@code ignoreUnknown}).
 *
 * <p>JSON snake_case alanları ({@code win_or_draw}, {@code under_over},
 * {@code poisson_distribution}) {@code @JsonProperty} ile camelCase record
 * component'lerine eşlenir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PredictionApiDto(
        Predictions predictions,
        Comparison comparison,
        /**
         * Ham {@code teams.home} ve {@code teams.away} performans verisi:
         * last_5, league form/fixtures/goals/cards/lineups, biggest,
         * clean_sheet, vb. JSONB olarak passthrough — frontend dogrudan kullanir.
         * Map yapisi Jackson tarafindan otomatik dolar; ic ic dict/list olarak.
         */
        Map<String, Object> teams
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Predictions(
            Winner winner,
            @JsonProperty("win_or_draw") Boolean winOrDraw,
            @JsonProperty("under_over") String underOver,
            Goals goals,
            String advice,
            Percent percent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Winner(Long id, String name, String comment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Goals(String home, String away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Percent(String home, String draw, String away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Comparison(
            Pair form,
            Pair att,
            Pair def,
            @JsonProperty("poisson_distribution") Pair poissonDistribution,
            Pair h2h,
            Pair goals,
            Pair total
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pair(String home, String away) {}
}

package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Basketball {@code /standings} yanit ogesi.
 *
 * <p><b>ONEMLI:</b> Bu endpoint'in response yapisi {@code List<List<BkStandingDto>>}
 * seklindedir — her grup ayri bir alt-dizi. NBA gibi liglerde "Western
 * Conference" / "Eastern Conference" ayri dizilerdir. Sync sirasinda
 * {@code flatMap} ile duzlestirilir ve {@code group.name} kullanilir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkStandingDto(
        String stage,
        Group group,
        TeamRef team,
        Integer position,
        Wins won,
        Wins lost,        // ayni yapida (all/home/away/percentage)
        GamesBlock games,
        PointsBlock points,
        String form,
        String description
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Group(String name, String points) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    /** {@code won} ve {@code lost} ortak yapi: all/home/away/percentage. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wins(Integer all, Integer home, Integer away, String percentage) {}

    /**
     * {@code games} bloku — sadece "played" ihtiyacimiz var (toplam/ev/dis).
     * "win" ve "lose" alanlari da var ama bunlar {@code won}/{@code lost}'la
     * cakisiyor; biz {@code won}/{@code lost} kullanalim.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamesBlock(Played played) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Played(Integer all, Integer home, Integer away) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PointsBlock(
            @com.fasterxml.jackson.annotation.JsonProperty("for") Integer pointsFor,
            Integer against) {}
}

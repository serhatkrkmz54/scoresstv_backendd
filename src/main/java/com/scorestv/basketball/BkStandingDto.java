package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Basketball {@code /standings} yanit ogesi — gercek API shape ile uyumlu.
 *
 * <p><b>ONEMLI:</b> Bu endpoint'in response yapisi {@code List<List<BkStandingDto>>}
 * seklindedir — her grup ayri bir alt-dizi. NBA gibi liglerde "Western
 * Conference" / "Eastern Conference" ayri dizilerdir. Sync sirasinda
 * {@code flatMap} ile duzlestirilir ve {@code stage} kullanilir.
 *
 * <p>Yapi (API'den ornek):
 * <pre>{
 *   "position": 1,
 *   "stage": "LNB - Apertura",
 *   "group": {"name": null, "points": 27},
 *   "team": {"id": 3900, "name": "...", "logo": "..."},
 *   "games": {
 *     "played": 14,
 *     "win":  {"total": 13, "percentage": "0.929"},
 *     "lose": {"total": 1,  "percentage": "0.071"}
 *   },
 *   "points": {"for": 1236, "against": 1032},
 *   "form": null,
 *   "description": "Promotion - LNB (Apertura - Winners)"
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkStandingDto(
        Integer position,
        String stage,
        Group group,
        TeamRef team,
        GamesBlock games,
        PointsBlock points,
        String form,
        String description
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Group(String name, Integer points) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    /**
     * {@code games} bloku — {@code played} toplam mac sayisi, {@code win}/
     * {@code lose} her biri {@code {total, percentage}} formatinda.
     *
     * <p><b>DEFANSIF:</b> Bazi liglerde API {@code played} field'ini Map
     * dondurebilir ({@code {all,home,away}}). Object tipinde tutuyoruz,
     * Upserter runtime check ile parse eder.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GamesBlock(
            Object played,
            WinLose win,
            WinLose lose) {}

    /** {@code win} ve {@code lose} ortak yapi: {@code total + percentage}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WinLose(Integer total, String percentage) {}

    /** Sayilar bloku — {@code "for"} reserved word oldugu icin JsonProperty. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PointsBlock(
            @com.fasterxml.jackson.annotation.JsonProperty("for") Integer pointsFor,
            Integer against) {}
}

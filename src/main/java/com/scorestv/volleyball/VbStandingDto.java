package com.scorestv.volleyball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * API-Volleyball {@code /standings} yanit ogesi.
 *
 * <p><b>ONEMLI:</b> Bu endpoint'in response yapisi
 * {@code List<List<VbStandingDto>>} seklindedir — her grup ayri bir alt-dizi.
 * Sync sirasinda {@code flatMap} ile duzlestirilir ve {@code stage} kullanilir.
 *
 * <p><b>Voleybol farki:</b> beraberlik (draw) YOK — sadece win/lose.
 * {@code goals.for/against} = TOPLAM SET sayisidir (kazanilan/kaybedilen set).
 *
 * <p>Yapi (API'den ornek):
 * <pre>{
 *   "position": 1,
 *   "stage": "...",
 *   "group": {"name": "..."},
 *   "team": {"id": 3900, "name": "...", "logo": "..."},
 *   "league": {...}, "country": {...},
 *   "games": {
 *     "played": 14,
 *     "win":  {"total": 13, "percentage": "0.929"},
 *     "lose": {"total": 1,  "percentage": "0.071"}
 *   },
 *   "goals": {"for": 42, "against": 10},
 *   "points": 39,
 *   "form": "WWLWL",
 *   "description": null
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VbStandingDto(
        Integer position,
        String stage,
        Group group,
        TeamRef team,
        GamesBlock games,
        GoalsBlock goals,
        Integer points,
        String form,
        String description
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Group(String name) {}

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

    /** {@code goals.for}/{@code goals.against} = toplam set sayisi (int). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoalsBlock(
            @com.fasterxml.jackson.annotation.JsonProperty("for") Integer setsFor,
            Integer against) {}
}

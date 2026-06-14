package com.scorestv.basketball;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * API-Basketball {@code /players?id=X&season=Y} ve {@code /players?league=X&season=Y}
 * yanit ogesi: oyuncu profili + sezonluk istatistikler.
 *
 * <p>{@code leagues} alani bir Map: key = lig id (string), value =
 * o ligdeki sezonluk istatistikler. Cogu oyuncu tek lig oynar; coklu lig
 * destegi (NBA + lig ulusal takim) icin map.
 *
 * <p>Defansif tasarim: tum alanlar nullable. API bazi alanlari farkli
 * formatlarda donebilir (orn. height bazen "1.91" string, bazen
 * {meters:"1.91"} obje). Pragmatik olarak Object kullanilan yerler
 * upserter'da runtime check ile parse edilir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BkPlayerDto(
        Long id,
        String firstname,
        String lastname,
        Birth birth,
        String country,
        Object height,         // String veya {meters: "1.91"} — defansif
        Object weight,         // String veya {kilograms: "84"} — defansif
        String college,
        Map<String, LeagueStat> leagues
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Birth(String date, String country) {}

    /**
     * Bir oyuncunun bir ligdeki sezonluk istatistikleri.
     * NUMERIC degerler {@code total} (int) + {@code average} (String "28.3").
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueStat(
            Integer jersey,
            String position,
            TeamRef team,
            Games games,
            Points points,
            ShotStat field_goals,
            ShotStat threepoint_goals,
            ShotStat freethrows_goals,
            ReboundStat rebounds,
            CountStat assists,
            CountStat steals,
            CountStat blocks,
            CountStat turnovers,
            CountStat fouls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Games(
            Integer appearences,
            String minutes        // "30:24" formatinda; parse caller'da
    ) {}

    /** Toplam + ortalama puanlar (PPG icin average degeri). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Points(Integer total, String average) {}

    /** Sayilik atisler: toplam (made), denemeler (attempts), yuzde, ort. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShotStat(
            Integer total,
            Integer attempts,
            String percentage,
            String average
    ) {}

    /** Ribaund yapilari — total/offence/defense + average. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReboundStat(
            Integer total,
            Integer offence,
            Integer defense,
            String average      // total ribaund average — RPG
    ) {}

    /** AST, STL, BLK, TO, PF gibi tek-sayi metricleri — total + average. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountStat(Integer total, String average) {}
}

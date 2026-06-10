package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /coachs?team=X} veya {@code /coachs?id=X} yaniti.
 *
 * <p>Tek tek koc bilgisi + {@code career[]} ile gecmis tum takim donemleri.
 * Takim sayfasinda yalniz mevcut koc gosterilir; career'dan bu takim
 * filtrelenirse "kocun bu takimda kac yildir oldugu" cikar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachApiDto(
        Long id,
        String name,
        String firstname,
        String lastname,
        Integer age,
        Birth birth,
        String nationality,
        String height,      // "192 cm" string
        String weight,
        String photo,
        Team team,
        List<CareerEntry> career
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Birth(
            String date,        // "1973-08-29"
            String place,
            String country
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Team(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CareerEntry(
            Team team,
            String start,       // "2018-07-01"
            String end          // null → halen devam ediyor
    ) {}
}

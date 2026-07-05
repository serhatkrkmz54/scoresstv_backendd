package com.scorestv.rankings.web;

import com.scorestv.football.domain.Team;

import java.util.Locale;
import java.util.Map;

/**
 * FIFA milli takım sıralaması indeksi — ülke KODU ve İSİM ile hızlı arama.
 *
 * <p>Bir milli takımın FIFA sırasını sırayla dener: önce 3-harf kod
 * ({@code team.code} ↔ FIFA {@code countryCode}; Türkiye/Türkiye gibi ad
 * uyuşmazlıklarını da kapsar), sonra isim ({@code team.name} / {@code country}
 * ↔ FIFA {@code teamName}). Kulüp veya milli-olmayan takımlarda {@code null}.
 */
public record FifaRankIndex(Map<String, Integer> byCode, Map<String, Integer> byName) {

    /** Milli takım ise FIFA sırası, değilse / eşleşmezse {@code null}. */
    public Integer rankFor(Team team) {
        if (team == null || !team.isNational()) {
            return null;
        }
        String code = team.getCode();
        if (code != null && !code.isBlank()) {
            Integer r = byCode.get(code.trim().toUpperCase(Locale.ROOT));
            if (r != null) {
                return r;
            }
        }
        String name = team.getName();
        if (name != null && !name.isBlank()) {
            Integer r = byName.get(name.trim().toLowerCase(Locale.ROOT));
            if (r != null) {
                return r;
            }
        }
        String country = team.getCountry();
        if (country != null && !country.isBlank()) {
            Integer r = byName.get(country.trim().toLowerCase(Locale.ROOT));
            if (r != null) {
                return r;
            }
        }
        return null;
    }
}

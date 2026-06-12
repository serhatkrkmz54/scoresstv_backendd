package com.scorestv.basketball.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Basketbol lig hub'i — futbol {@code LeagueHubResponse}'un basketbol kardeşi.
 * Ülke bazında basketbol liglerini gruplar; mobile onboarding accordion'unda
 * favori takım seçimi için kullanılır.
 *
 * <p>URL: {@code GET /api/v1/mobile/basketball/leagues/hub?lang=&country=&search=}.
 *
 * <p>Futbolun aksine "covered" / "hasStandings" gibi bayraklar yok — basketbol
 * mobile şu an sadece canlı skor + favori maç bildirim'i içeriyor; standings
 * detayı sonraki faz.
 */
public record BasketballLeagueHubResponse(
        /** Toplam dahil edilen lig sayısı (tüm gruplar toplamı). */
        int totalLeagues,
        /** Ülke bazında gruplanmış basketbol ligleri (alfabetik). */
        List<CountryGroup> countries
) implements Serializable {

    public record CountryGroup(
            /** Dile göre ülke adı (TR: name_tr, EN: name). */
            String name,
            String code,
            String flag,
            List<LeagueRef> leagues
    ) implements Serializable {}

    public record LeagueRef(
            Long id,
            /** Dile göre lig adı (TR: name_tr, yoksa name). */
            String name,
            String logo,
            /** API'den gelen tip ("League" / "Cup"). */
            String type,
            /** Güncel sezon — örn "2024-2025". */
            String currentSeason
    ) implements Serializable {}
}

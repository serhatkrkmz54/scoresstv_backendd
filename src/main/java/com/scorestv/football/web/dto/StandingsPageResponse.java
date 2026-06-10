package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Puan durumu sayfasi yaniti — LIG sayfasindan farkli, daha hafif ve focused.
 *
 * <p>Lig sayfasi ({@link LeagueDetailResponse}) tum widget'lari icerir:
 * fixtures, top scorers/assists/cards, SEO, vs. Puan durumu sayfasi sadece
 * 3 widget'a odaklanir:
 * <ul>
 *   <li><b>Lig meta + dropdown'lar</b> — picker'dan secilen lig + sezon</li>
 *   <li><b>Standings</b> (lig icin gruplu tablo) veya
 *       <b>bracket</b> (kupa icin eleme agaci)</li>
 *   <li><b>Top 20 rating</b> — ligin en iyi 20 oyuncusu rating'e gore</li>
 * </ul>
 *
 * <p>SEO yok — bu bir uygulama-ici (app-internal) sayfa. Lig sayfasi ayrica
 * SEO'lu kalir. Top scorers/cards/fixtures lig sayfasindan cekilir.
 *
 * <p>Endpoint: {@code GET /api/v1/standings/{leagueSlug}?season=&lang=}
 */
public record StandingsPageResponse(
        /** Lig ozeti — picker'dan secilen lig. */
        LeagueMeta league,
        /** Cagrida secilmis sezon (?season= ya da currentSeason). */
        Integer selectedSeason,
        /** Tum sezonlar, yeni → eski. UI dropdown'da listelenir. */
        List<SeasonInfo> seasons,
        /** Sezona ozgu coverage — "puan durumu yok" gibi UI hint'ler icin. */
        CoverageInfo coverage,
        /**
         * Puan durumu — gruplara bolunmus (kupada grup asamasi, ligde tek grup).
         * Cup'ta knockout faza ulasilirsa bos da olabilir; o zaman bracket dolu.
         */
        List<StandingsGroup> standings,
        /**
         * Kupa eleme bracket — yalniz {@code league.rawType="Cup"} ise dolu.
         * Lig (League) tipi liglerde null.
         */
        BracketView bracket,
        /**
         * Rating'e gore en iyi 20 oyuncu. Lig icin tum oyuncular, kupa icin
         * o kupada oynamis oyuncular. Standings sayfasinin "ligin en iyileri"
         * widget'i. Minimum 5 mac oynamis oyunculardan.
         */
        List<LeagueDetailResponse.TopRatedPlayer> topRatedPlayers
) implements Serializable {

    public record LeagueMeta(
            Long id,
            /** SEO slug — "premier-league-39". */
            String slug,
            /** Dile gore lig adi (TR ise name_tr, yoksa name). */
            String name,
            /** "Lig" / "Kupa" — dile gore. */
            String type,
            /** Raw type — "League" / "Cup" — UI bracket/standings ayrimi. */
            String rawType,
            String logo,
            Country country,
            Integer currentSeason
    ) implements Serializable {}

    public record Country(
            String name,
            String code,
            String flag
    ) implements Serializable {}

    public record SeasonInfo(
            Integer year,
            LocalDate startDate,
            LocalDate endDate,
            boolean current
    ) implements Serializable {}

    /**
     * Sezona ozgu coverage. Frontend bayraklara bakip "puan durumu henuz
     * olusmadi", "rating verisi yok" gibi guvenli gostergeler cikarir.
     */
    public record CoverageInfo(
            boolean standings,
            boolean statsPlayers
    ) implements Serializable {}
}

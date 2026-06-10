package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Lig detay sayfasi yaniti — genel bilgiler + sezonlar + secili sezon
 * standings + secili sezon round bazli fikstur + top scorers/assists/cards.
 *
 * <p>Frontend bir lig sayfasi acildiginda bunu cagirir. Sezon secimi
 * {@code ?season=} ile yapilir; param yoksa lig'in current sezonu kullanilir.
 * {@code selectedSeason} cagrida bulunulan sezonu doner — frontend tab/dropdown
 * vurgusu icin kullanir.
 */
public record LeagueDetailResponse(
        Long id,
        /** SEO slug — "premier-league-39" (EN) ya da "premier-lig-39" (TR). */
        String slug,
        /** Dile gore lig adi (TR ise name_tr, yoksa name). */
        String name,
        /** "Lig" / "Kupa" — dile gore. */
        String type,
        String logo,
        /** Ulke ozeti. */
        Country country,
        /** Lig'in suanki gecerli sezonu — UI default secimi. */
        Integer currentSeason,
        /** Cagrida secilmis sezon (?season= param ya da currentSeason). */
        Integer selectedSeason,
        /** Tum sezonlar, yeni → eski. UI dropdown'da listelenir. */
        List<SeasonInfo> seasons,
        /** Secili sezon coverage bayraklari (UI "puan durumu yok" gostergesi). */
        CoverageInfo coverage,
        /** Secili sezonun puan durumu — gruplara bolunmus. */
        List<StandingsGroup> standings,
        /** Secili sezonun fixtures'lari round (hafta) bazli gruplu. */
        List<RoundGroup> rounds,
        /** Gol kralları. Coverage'da yoksa bos liste. */
        List<TopPlayerView> topScorers,
        /** Asist krallari. Coverage'da yoksa bos liste. */
        List<TopPlayerView> topAssists,
        /**
         * En cok sari kart goren oyuncular.
         * {@code value} = sari kart sayisi, {@code valueSecondary} = cift sariden
         * kirmizi sayisi (yellowred). Coverage yoksa bos.
         */
        List<TopPlayerView> topYellowCards,
        /**
         * En cok kirmizi kart goren oyuncular.
         * {@code value} = direkt kirmizi kart sayisi. Coverage yoksa bos.
         */
        List<TopPlayerView> topRedCards,
        /**
         * Rating'e gore en iyi 20 oyuncu (player_season_stats'tan). Standings
         * sayfasinin "ligin en iyileri" widget'i icin. JSONB passthrough —
         * frontend istedigi alani gosterir (rating, appearences, goals,
         * assists, vs.). Minimum 5 mac oynamis oyunculardan secilir.
         */
        List<TopRatedPlayer> topRatedPlayers,
        /**
         * Kupa eleme bracket — yalniz {@code type="Cup"} ligler icin doludur.
         * Lig (ligue) tipi liglerde null. Cup'ta grup asamasi varsa
         * {@code standings} ile grup tablolari, {@code bracket} ile knockout
         * gosterilir. Sampiyon (varsa) {@code bracket.champion}'da.
         */
        BracketView bracket,
        LeagueSeoResponse seo
) implements Serializable {

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
     * olusmadi", "kart liderleri yok" gibi guvenli gostergeler cikarir.
     */
    public record CoverageInfo(
            boolean standings,
            boolean events,
            boolean lineups,
            boolean statsFixtures,
            boolean statsPlayers,
            boolean players,
            boolean topScorers,
            boolean topAssists,
            boolean topCards,
            boolean injuries,
            boolean predictions,
            boolean odds
    ) implements Serializable {}

    /**
     * Round (hafta) bazli fixtures grubu. {@code roundName} ham API degeri
     * ("Regular Season - 17"), {@code roundNameText} dile cevrilmis ("Hafta 17").
     */
    public record RoundGroup(
            String roundName,
            String roundNameText,
            List<FixtureSummary> fixtures
    ) implements Serializable {}

    /** Tek bir top-N oyuncu satiri (UI'da kart). */
    public record TopPlayerView(
            Integer rank,
            Long playerId,
            String playerName,
            String playerPhoto,
            String playerNationality,
            Integer playerAge,
            Long teamId,
            String teamName,
            String teamLogo,
            /** Takım detay slug'i (besiktas-549). Frontend /team/ veya /takim/ ekler. */
            String teamSlug,
            /** Birincil deger — scorers: goller, assists: asistler, cards: sari kart. */
            Integer value,
            /** Cards icin kirmizi kart sayisi (digerleri null). */
            Integer valueSecondary,
            Integer appearances,
            Integer minutes
    ) implements Serializable {}

    /**
     * Rating'e gore en iyi oyuncu satiri (standings sayfasi widget'i).
     * Oyuncu + takim ozeti + ham JSONB stat passthrough — frontend istedigi
     * alani gosterir (rating, appearences, goals, assists, minutes, vs.).
     *
     * @param rank Siralamadaki yer (1 = en yuksek rating)
     * @param rating Sayisal rating degeri (orn. 7.85). Direkt sorting icin
     *               ayri alan — frontend stats JSON'una bakmadan gosterir.
     */
    public record TopRatedPlayer(
            Integer rank,
            Long playerId,
            String playerName,
            String playerSlug,
            String playerPhoto,
            String playerNationality,
            Integer playerAge,
            String position,
            String positionText,
            Long teamId,
            String teamName,
            String teamLogo,
            String teamSlug,
            /** Sayisal rating (orn. 7.85) — sorting icin numerik. */
            java.math.BigDecimal rating,
            /** Tam JSONB passthrough — istedigi alani frontend cikarir. */
            java.util.Map<String, Object> stats
    ) implements Serializable {}
}

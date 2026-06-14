package com.scorestv.basketball.web.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Basketbol lig detay sayfasi yaniti — genel bilgiler + sezonlar + secili
 * sezon standings + son/yaklasik fikstur + 3 kategori top players.
 *
 * <p>Futboldaki {@code LeagueDetailResponse} patternine paralel ama basketbol
 * sade: round (hafta) bazli gruplama YOK (NBA'de sezon akiskan), bracket YOK
 * (cup playoff'lar standings stage ile coevrilir), 3 kategori top (SCORERS /
 * REBOUNDERS / ASSISTS).
 *
 * <p>Sezon parametresi {@code ?season=YYYY-YYYY} ile gelir; yoksa lig'in
 * current sezonu kullanilir. {@code selectedSeason} alani frontend dropdown
 * vurgusu icin.
 */
public record BasketballLeagueDetailResponse(
        Long id,
        /** SEO slug — "nba" / "euroleague" / "tr-basketbol-super-ligi". */
        String slug,
        /** Dile gore lig adi (TR ise name_tr, yoksa name). */
        String name,
        /** "Lig" / "Kupa" — dile gore. */
        String type,
        String logo,
        /** Ulke ozeti. */
        Country country,
        /** Lig'in suanki gecerli sezonu — UI default secimi (orn. "2024-2025"). */
        String currentSeason,
        /** Cagrida secilmis sezon (?season= param ya da currentSeason). */
        String selectedSeason,
        /** Tum sezonlar, yeni → eski. UI dropdown'da listelenir. */
        List<SeasonInfo> seasons,
        /** Secili sezon coverage bayraklari. */
        CoverageInfo coverage,
        /**
         * Secili sezonun puan durumu — gruplara bolunmus (NBA: East/West,
         * EuroLeague: Final Four icin Group A/B). Lean ozet listesi degil;
         * frontend istedigini gosterir.
         */
        List<StandingsGroup> standings,
        /** Secili sezonun son N maci (FT) — tarih DESC. */
        List<GameSummary> recentGames,
        /** Secili sezonun yaklasik N maci (NS) — tarih ASC. */
        List<GameSummary> upcomingGames,
        /** En cok sayi atanlar (PPG sirali). Coverage yoksa bos. */
        List<TopPlayerView> topScorers,
        /** En cok ribaund alanlar (RPG sirali). Coverage yoksa bos. */
        List<TopPlayerView> topRebounders,
        /** En cok asist yapanlar (APG sirali). Coverage yoksa bos. */
        List<TopPlayerView> topAssists,
        BasketballLeagueSeoResponse seo
) implements Serializable {

    public record Country(
            String name,
            String code,
            String flag
    ) implements Serializable {}

    /**
     * Bir sezon kaydi. start/end null olabilir (eski sezonlarda tarih yok).
     * coverage ilgili sezonun veri durumunu gosterir.
     */
    public record SeasonInfo(
            String season,        // "2024-2025"
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            CoverageInfo coverage
    ) implements Serializable {}

    /**
     * Sezona ozgu coverage. Frontend bayraklara bakip "puan durumu yok",
     * "oyuncu listesi yok" gibi guvenli gostergeler cikarir.
     */
    public record CoverageInfo(
            boolean statsTeams,
            boolean statsPlayers,
            boolean standings,
            boolean players,
            boolean odds
    ) implements Serializable {}

    /**
     * Standings grubu — basketbol_standings_page'den passthrough.
     * Frontend bunu olduğu gibi render eder (mevcut StandingsTab widget'i).
     */
    public record StandingsGroup(
            String groupName,       // "Eastern Conference" / "Group A" / ""
            String stage,           // "Regular Season" / "Final Four"
            List<StandingRow> rows
    ) implements Serializable {}

    public record StandingRow(
            Integer position,
            TeamRef team,
            Integer gamesPlayed,
            Integer won,
            Integer lost,
            String winPercentage,
            Integer pointsFor,
            Integer pointsAgainst,
            Integer pointsDifference,
            String form,
            String description,
            String descriptionText
    ) implements Serializable {}

    /**
     * Hafif mac satiri — fikstür/recent/upcoming için. Game detay link slug
     * dahil; tarih, statu, skor, takim adlari + logolar.
     */
    public record GameSummary(
            Long id,
            String slug,
            OffsetDateTime kickoff,
            String statusShort,    // "NS" / "FT" / "Q1" / ...
            String statusText,     // "Maç Başlamadı" / "Maç Sonu"
            TeamRef homeTeam,
            TeamRef awayTeam,
            Integer homeTotal,
            Integer awayTotal,
            String stage,
            String week
    ) implements Serializable {}

    public record TeamRef(
            Long id,
            String name,
            String slug,
            String logo
    ) implements Serializable {}

    /**
     * Top players satiri — kategori basina top 10. value siralama metrigi
     * (PPG/RPG/APG), gamesPlayed referans.
     */
    public record TopPlayerView(
            Integer rank,
            Long playerId,
            String playerName,
            String playerSlug,
            String playerPhoto,
            String playerNationality,
            TeamRef team,
            String value,          // "28.3" gibi — NUMERIC string
            Integer gamesPlayed
    ) implements Serializable {}
}

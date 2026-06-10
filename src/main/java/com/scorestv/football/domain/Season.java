package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Bir ligin tek bir sezonu. /leagues yanıtında her ligin altında gömülü gelir.
 * (lig, yıl) ikilisi benzersizdir.
 *
 * <p>Coverage bayrakları: API-Football'un her sezon için "şu verim var" listesi.
 * Sezon başladıktan sonra true olur; başlamadan önce hepsi false. UI bu
 * bayraklara bakarak "puan durumu henüz oluşmadı" gibi göstergeler çıkarır;
 * lazy sync de "veri yok" diye boş yere API çağırmaz.
 */
@Entity
@Table(
        name = "seasons",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seasons_league_year",
                columnNames = {"league_id", "season_year"})
)
@Getter
@Setter
@NoArgsConstructor
public class Season extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    /** Sezon yılı (örn. 2025). */
    @Column(name = "season_year", nullable = false)
    private Integer year;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Bu, ligin geçerli (güncel) sezonu mu? */
    @Column(name = "is_current", nullable = false)
    private boolean current;

    // --- Coverage bayraklari (API /leagues yanitindan) ---

    @Column(name = "coverage_standings", nullable = false)
    private boolean coverageStandings;

    @Column(name = "coverage_events", nullable = false)
    private boolean coverageEvents;

    @Column(name = "coverage_lineups", nullable = false)
    private boolean coverageLineups;

    @Column(name = "coverage_stats_fixtures", nullable = false)
    private boolean coverageStatsFixtures;

    @Column(name = "coverage_stats_players", nullable = false)
    private boolean coverageStatsPlayers;

    @Column(name = "coverage_players", nullable = false)
    private boolean coveragePlayers;

    @Column(name = "coverage_top_scorers", nullable = false)
    private boolean coverageTopScorers;

    @Column(name = "coverage_top_assists", nullable = false)
    private boolean coverageTopAssists;

    @Column(name = "coverage_top_cards", nullable = false)
    private boolean coverageTopCards;

    @Column(name = "coverage_injuries", nullable = false)
    private boolean coverageInjuries;

    @Column(name = "coverage_predictions", nullable = false)
    private boolean coveragePredictions;

    @Column(name = "coverage_odds", nullable = false)
    private boolean coverageOdds;
}

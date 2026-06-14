package com.scorestv.basketball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bir oyuncunun belirli bir lig + sezondaki ortalama istatistikleri.
 *
 * <p>API-Basketball {@code /players?id=X&season=Y} endpoint'inden tek
 * satir doner; bu entity onun normalize hali. Ortalama degerler NUMERIC
 * (BigDecimal) tipinde (PPG = points per game, ondalik).
 *
 * <p>{@code BasketballLeagueTopPlayer} entity'si bu tablodaki PPG/RPG/APG
 * degerlerini siralayarak doldurulur — yani bu tablo "kaynak", top players
 * "view".
 *
 * <p>Unique: (player_id, league_id, season) — bir oyuncu, bir lig, bir
 * sezon = tek satir. Mid-season transfer durumunda team_id en son bilinen.
 */
@Entity
@Table(
        name = "basketball_player_season_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bk_player_season",
                columnNames = {"player_id", "league_id", "season"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballPlayerSeasonStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private BasketballPlayer player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private BasketballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private BasketballTeam team;

    // ---- Mac bazli ----
    @Column private Integer gamesPlayed;
    @Column private Integer gamesStarted;
    @Column(precision = 5, scale = 2) private BigDecimal minutesPerGame;

    // ---- Skorlama ----
    @Column(precision = 5, scale = 2) private BigDecimal pointsPerGame;
    @Column(precision = 5, scale = 2) private BigDecimal fieldGoalsMade;
    @Column(precision = 5, scale = 2) private BigDecimal fieldGoalsAttempts;
    @Column(precision = 5, scale = 2) private BigDecimal fieldGoalsPct;
    @Column(precision = 5, scale = 2) private BigDecimal threepointMade;
    @Column(precision = 5, scale = 2) private BigDecimal threepointAttempts;
    @Column(precision = 5, scale = 2) private BigDecimal threepointPct;
    @Column(precision = 5, scale = 2) private BigDecimal freethrowsMade;
    @Column(precision = 5, scale = 2) private BigDecimal freethrowsAttempts;
    @Column(precision = 5, scale = 2) private BigDecimal freethrowsPct;

    // ---- Ribaund ----
    @Column(precision = 5, scale = 2) private BigDecimal reboundsTotal;
    @Column(precision = 5, scale = 2) private BigDecimal reboundsOffence;
    @Column(precision = 5, scale = 2) private BigDecimal reboundsDefense;

    // ---- Diger ----
    @Column(precision = 5, scale = 2) private BigDecimal assistsPerGame;
    @Column(precision = 5, scale = 2) private BigDecimal stealsPerGame;
    @Column(precision = 5, scale = 2) private BigDecimal blocksPerGame;
    @Column(precision = 5, scale = 2) private BigDecimal turnoversPerGame;
    @Column(precision = 5, scale = 2) private BigDecimal foulsPerGame;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}

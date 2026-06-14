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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bir takimin belirli bir lig + sezondaki ozet istatistikleri.
 *
 * <p>API-Basketball {@code /teams/statistics?team=X&league=Y&season=Z}
 * yanitinin normalize hali. Toplam W/L sayilari + ortalamalar + en uzun
 * seriler basit kolonlar; ev/deplasman breakdown'i ise {@code home_away_json}
 * JSONB icinde tutulur (degisken sayida iç alan iceriyor).
 *
 * <p>{@code BasketballTeamSeasonStatRepository} yalnizca lookup amaciyla
 * kullanilir — entity {@code BasketballTeamDetailResponse#statistics}
 * blogunu doldurur.
 *
 * <p>Unique: (team_id, league_id, season).
 */
@Entity
@Table(
        name = "basketball_team_season_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bk_team_season_stats",
                columnNames = {"team_id", "league_id", "season"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballTeamSeasonStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private BasketballTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private BasketballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    // ---- Toplam (all) bloku ----
    private Integer gamesPlayed;
    private Integer wins;
    private Integer loses;

    /** Galibiyet yuzdesi (0.000 - 1.000). */
    @Column(precision = 6, scale = 3)
    private BigDecimal winPercentage;

    // ---- Skor ortalamalari ----
    private Integer pointsForTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal pointsForAvg;

    private Integer pointsAgainstTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal pointsAgainstAvg;

    // ---- Seriler ----
    private Integer longestWinStreak;
    private Integer longestLoseStreak;

    /** Son maclar formu (orn. "WWLWW"). */
    @Column(length = 40)
    private String form;

    /**
     * Ev/deplasman breakdown JSONB. Sema:
     * <pre>{
     *   "home": {"played":N, "wins":N, "loses":N, "pointsFor":N, "pointsAgainst":N},
     *   "away": {"played":N, "wins":N, "loses":N, "pointsFor":N, "pointsAgainst":N}
     * }</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "home_away_json", columnDefinition = "jsonb")
    private String homeAwayJson;

    @UpdateTimestamp
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}

package com.scorestv.volleyball.domain;

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
 * <p>API-Volleyball {@code /teams/statistics?team=X&league=Y&season=Z}
 * yanitinin normalize hali. {@code setsFor/Against} = kazanilan/kaybedilen
 * set toplami + ortalamalari. Ev/deplasman breakdown JSONB icinde.
 *
 * <p>Unique: (team_id, league_id, season).
 */
@Entity
@Table(
        name = "volleyball_team_season_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vb_team_season_stats",
                columnNames = {"team_id", "league_id", "season"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class VolleyballTeamSeasonStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private VolleyballTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private VolleyballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    // ---- Toplam (all) bloku ----
    private Integer gamesPlayed;
    private Integer wins;
    private Integer loses;

    /** Galibiyet yuzdesi (0.000 - 1.000). */
    @Column(precision = 6, scale = 3)
    private BigDecimal winPercentage;

    // ---- Set ortalamalari (goals = set/sayi) ----
    private Integer setsForTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal setsForAvg;

    private Integer setsAgainstTotal;

    @Column(precision = 8, scale = 2)
    private BigDecimal setsAgainstAvg;

    /** Son maclar formu (orn. "WWLWW"). */
    @Column(length = 40)
    private String form;

    /**
     * Ev/deplasman breakdown JSONB. Sema:
     * <pre>{
     *   "home": {"played":N, "wins":N, "loses":N, "setsForAvg":N, "setsAgainstAvg":N},
     *   "away": {...}
     * }</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "home_away_json", columnDefinition = "jsonb")
    private String homeAwayJson;

    @UpdateTimestamp
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}

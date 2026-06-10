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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Bir oyuncunun belirli takim + lig + sezondaki aggregated istatistikleri.
 * API:
 *   {@code GET /players?team=X&season=Y}  (sayfali)
 *
 * <p>Bir oyuncu ayni sezonda birden fazla turnuvada oynayabilir (lig + kupa +
 * CL); her turnuva ayri satir. {@code stats_json} API yanitindaki statistics[]
 * elementinin birebir kopyasi — games/goals/shots/passes/tackles/duels/
 * dribbles/fouls/cards/penalty/substitutes alanlari icerir. Frontend hangi
 * alana ihtiyaci varsa oradan alir; {@link com.scorestv.football.domain.TeamStatistics}
 * ile ayni passthrough desen.
 */
@Entity
@Table(
        name = "player_season_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_player_season_stats",
                columnNames = {"player_id", "team_id", "league_id", "season"})
)
@Getter
@Setter
@NoArgsConstructor
public class PlayerSeasonStat extends BaseEntity {

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private Integer season;

    /** API'nin statistics[] elementinin tam kopyasi (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> statsJson;
}

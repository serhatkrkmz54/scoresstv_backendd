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
 * Bir takimin bir lig+sezondaki istatistikleri. API:
 *   {@code GET /teams/statistics?team=X&league=Y&season=Z}
 *
 * <p>Yanit cok zengin (form, fixtures, goals/minute/under_over, biggest,
 * clean_sheet, failed_to_score, penalty, lineups, cards). Predictions
 * teams_json'da uyguladigimiz desenle JSONB passthrough — frontend
 * dogrudan kullanir.
 */
@Entity
@Table(
        name = "team_statistics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_team_statistics_team_league_season",
                columnNames = {"team_id", "league_id", "season"})
)
@Getter
@Setter
@NoArgsConstructor
public class TeamStatistics extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private Integer season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> statsJson;
}

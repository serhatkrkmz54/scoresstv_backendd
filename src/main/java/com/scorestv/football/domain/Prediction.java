package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Bir maçın tahmini (winner + percent + comparison).
 *
 * <p>Maç başına TEK kayıt — {@code UNIQUE(fixture_id)} ile garanti. UPSERT
 * pattern: varsa update, yoksa insert.
 *
 * <p>Karşılaştırma değerleri tümü VARCHAR — API "60%", "1.2" gibi
 * formatlanmış string döner. Frontend parse eder (radar chart, progress bar).
 */
@Entity
@Table(
        name = "predictions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_predictions_fixture",
                columnNames = "fixture_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Prediction extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false, unique = true)
    private Fixture fixture;

    // --- predictions.winner ---
    @Column(name = "winner_team_id")
    private Long winnerTeamId;

    @Column(name = "winner_comment", length = 120)
    private String winnerComment;

    @Column(name = "win_or_draw")
    private Boolean winOrDraw;

    // --- predictions ---
    @Column(length = 255)
    private String advice;

    @Column(name = "under_over", length = 8)
    private String underOver;

    @Column(name = "goals_home", length = 8)
    private String goalsHome;

    @Column(name = "goals_away", length = 8)
    private String goalsAway;

    // --- predictions.percent (home/draw/away kazanma %) ---
    @Column(name = "percent_home", length = 8)
    private String percentHome;

    @Column(name = "percent_draw", length = 8)
    private String percentDraw;

    @Column(name = "percent_away", length = 8)
    private String percentAway;

    // --- comparison (radar chart için) ---
    @Column(name = "comparison_form_home", length = 8) private String comparisonFormHome;
    @Column(name = "comparison_form_away", length = 8) private String comparisonFormAway;
    @Column(name = "comparison_att_home", length = 8) private String comparisonAttHome;
    @Column(name = "comparison_att_away", length = 8) private String comparisonAttAway;
    @Column(name = "comparison_def_home", length = 8) private String comparisonDefHome;
    @Column(name = "comparison_def_away", length = 8) private String comparisonDefAway;
    @Column(name = "comparison_poisson_home", length = 8) private String comparisonPoissonHome;
    @Column(name = "comparison_poisson_away", length = 8) private String comparisonPoissonAway;
    @Column(name = "comparison_h2h_home", length = 8) private String comparisonH2hHome;
    @Column(name = "comparison_h2h_away", length = 8) private String comparisonH2hAway;
    @Column(name = "comparison_goals_home", length = 8) private String comparisonGoalsHome;
    @Column(name = "comparison_goals_away", length = 8) private String comparisonGoalsAway;
    @Column(name = "comparison_total_home", length = 8) private String comparisonTotalHome;
    @Column(name = "comparison_total_away", length = 8) private String comparisonTotalAway;

    /**
     * API'nin {@code teams.home} ve {@code teams.away} altindaki tum zengin
     * performans verisi (last_5, league form/fixtures/goals/cards/lineups,
     * biggest, clean_sheet, ...). 80+ alani ayri kolon yapmak yerine JSONB
     * olarak saklariz; frontend dogrudan kullanir.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} Hibernate 6'nin native JSONB
     * mapping'ini aktive eder — PostgreSQL JSONB <-> Map<String, Object>
     * donusumu otomatik.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "teams_json", columnDefinition = "jsonb")
    private Map<String, Object> teamsJson;
}

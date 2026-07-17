package com.scorestv.football.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Bir maç için AI modelinin MAÇTAN ÖNCE verdiği tahmin + maç bitince notlama.
 * Aylık/yıllık isabet istatistiklerinin kaynağı. Kayıt maçtan önce yapılır
 * (leakage yok); notlama {@code AiPredictionRecorder.gradeFinished} ile.
 */
@Entity
@Table(name = "ai_prediction")
@Getter
@Setter
@NoArgsConstructor
public class AiPrediction {

    @Id
    @Column(name = "fixture_id")
    private Long fixtureId;

    @Column(name = "league_id")
    private Long leagueId;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    // ---- Model anlık görüntüsü ----
    @Column(name = "favorite")
    private String favorite;

    @Column(name = "home_win_pct")
    private Integer homeWinPct;

    @Column(name = "draw_pct")
    private Integer drawPct;

    @Column(name = "away_win_pct")
    private Integer awayWinPct;

    @Column(name = "over25_pct")
    private Integer over25Pct;

    @Column(name = "btts_yes_pct")
    private Integer bttsYesPct;

    @Column(name = "exp_home")
    private Double expHome;

    @Column(name = "exp_away")
    private Double expAway;

    @Column(name = "expected_score")
    private String expectedScore;

    @Column(name = "confidence")
    private String confidence;

    // ---- Türetilmiş tahminler ----
    @Column(name = "pick_result")
    private String pickResult; // HOME | DRAW | AWAY | null

    @Column(name = "pick_ou")
    private String pickOu; // OVER | UNDER

    @Column(name = "pick_btts")
    private String pickBtts; // YES | NO

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // ---- Notlama ----
    @Column(name = "graded", nullable = false)
    private boolean graded = false;

    @Column(name = "actual_home")
    private Integer actualHome;

    @Column(name = "actual_away")
    private Integer actualAway;

    @Column(name = "result_hit")
    private Boolean resultHit;

    @Column(name = "ou_hit")
    private Boolean ouHit;

    @Column(name = "btts_hit")
    private Boolean bttsHit;

    @Column(name = "exact_hit")
    private Boolean exactHit;

    @Column(name = "graded_at")
    private Instant gradedAt;
}

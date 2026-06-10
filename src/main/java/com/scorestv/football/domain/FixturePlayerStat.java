package com.scorestv.football.domain;

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

import java.time.Instant;

/**
 * Bir maçta tek bir oyuncunun performans istatistikleri.
 *
 * <p>API-Football {@code /fixtures/players} her oyuncu için nested objeler
 * verir (games/shots/goals/passes/...). Burada flat sütunlara açılır;
 * "X oyuncunun son 10 maçtaki ortalaması" gibi sorgular DB tarafında çalışır.
 *
 * <p>Sync replace pattern: senkronda o maçın tüm oyuncu satırları silinip
 * tam set yeniden yazılır.
 *
 * <p>{@code rating} ve {@code passes_accuracy}: API String döner ("7.2", "60")
 * — VARCHAR olarak saklanır. {@code penalty_committed}: API'de "commited"
 * (typo); upserter eşler.
 */
@Entity
@Table(
        name = "fixture_player_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fixture_player_stats",
                columnNames = {"fixture_id", "player_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class FixturePlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** API-Football player id (FK değil — oyuncu tablosu henüz yok). */
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 120)
    private String playerName;

    @Column(name = "player_photo", length = 255)
    private String playerPhoto;

    // --- games bloku ---
    @Column private Integer minutes;
    @Column(name = "jersey_number") private Integer jerseyNumber;
    @Column(length = 5) private String position;
    @Column(length = 8) private String rating;
    @Column private Boolean captain;
    @Column private Boolean substitute;

    // --- tek alanlar ---
    @Column private Integer offsides;

    // --- shots ---
    @Column(name = "shots_total") private Integer shotsTotal;
    @Column(name = "shots_on") private Integer shotsOn;

    // --- goals ---
    @Column(name = "goals_total") private Integer goalsTotal;
    @Column(name = "goals_conceded") private Integer goalsConceded;
    @Column(name = "goals_assists") private Integer goalsAssists;
    @Column(name = "goals_saves") private Integer goalsSaves;

    // --- passes ---
    @Column(name = "passes_total") private Integer passesTotal;
    @Column(name = "passes_key") private Integer passesKey;
    @Column(name = "passes_accuracy", length = 8) private String passesAccuracy;

    // --- tackles ---
    @Column(name = "tackles_total") private Integer tacklesTotal;
    @Column(name = "tackles_blocks") private Integer tacklesBlocks;
    @Column(name = "tackles_interceptions") private Integer tacklesInterceptions;

    // --- duels ---
    @Column(name = "duels_total") private Integer duelsTotal;
    @Column(name = "duels_won") private Integer duelsWon;

    // --- dribbles ---
    @Column(name = "dribbles_attempts") private Integer dribblesAttempts;
    @Column(name = "dribbles_success") private Integer dribblesSuccess;
    @Column(name = "dribbles_past") private Integer dribblesPast;

    // --- fouls ---
    @Column(name = "fouls_drawn") private Integer foulsDrawn;
    @Column(name = "fouls_committed") private Integer foulsCommitted;

    // --- cards ---
    @Column(name = "cards_yellow") private Integer cardsYellow;
    @Column(name = "cards_red") private Integer cardsRed;

    // --- penalty (API'de "commited" typo; biz doğru yazımla saklarız) ---
    @Column(name = "penalty_won") private Integer penaltyWon;
    @Column(name = "penalty_committed") private Integer penaltyCommitted;
    @Column(name = "penalty_scored") private Integer penaltyScored;
    @Column(name = "penalty_missed") private Integer penaltyMissed;
    @Column(name = "penalty_saved") private Integer penaltySaved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

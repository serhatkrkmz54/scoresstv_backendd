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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Mac basina oyuncu istatistikleri (game basina N satir — kadro tamami).
 *
 * <p>API: {@code /games/statistics/players?id=X}. {@code type} = "starters"
 * veya "bench". {@code playerName} defansif kopya — player master eksikse
 * yine de gosterilebilsin (oyuncu profil sayfasina link kirilir ama satir
 * cizilir).
 */
@Entity
@Table(
    name = "basketball_game_player_stats",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bk_game_player_stats",
        columnNames = {"game_id", "player_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballGamePlayerStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private BasketballGame game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private BasketballTeam team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private BasketballPlayer player;

    @Column(length = 160)
    private String playerName;

    /** "starters" / "bench" */
    @Column(length = 20)
    private String type;

    /** "32:15" formatinda string (API ham). */
    @Column(length = 10)
    private String minutes;

    // ---- Field goals (2pt + 3pt) ----
    private Integer fgTotal;
    private Integer fgAttempts;
    @Column(length = 10)
    private String fgPercentage;

    // ---- 3-point ----
    private Integer tpTotal;
    private Integer tpAttempts;
    @Column(length = 10)
    private String tpPercentage;

    // ---- Free throws ----
    private Integer ftTotal;
    private Integer ftAttempts;
    @Column(length = 10)
    private String ftPercentage;

    // ---- Ribauntlar ----
    private Integer reboundsTotal;
    private Integer reboundsOffence;
    private Integer reboundsDefense;

    private Integer assists;
    private Integer points;
    private Integer steals;
    private Integer blocks;
    private Integer turnovers;
    private Integer personalFouls;

    @UpdateTimestamp
    private Instant updatedAt;
}

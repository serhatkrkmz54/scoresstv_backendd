package com.scorestv.game;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Aynı mevkiden iki oyuncunun bir istatistikte kapışması. Oyuncu bilgisi
 *  denormalize (UI ekstra join yapmadan gösterir). */
@Entity
@Table(name = "game_duel")
@Getter
@Setter
public class GameDuel extends BaseEntity {

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DuelPosition position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DuelMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private DuelDirection direction = DuelDirection.HIGHER;

    @Column(name = "league_id")
    private Long leagueId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "player_a_id", nullable = false)
    private Long playerAId;
    @Column(name = "player_a_name", length = 120)
    private String playerAName;
    @Column(name = "player_a_photo", length = 255)
    private String playerAPhoto;
    @Column(name = "player_a_team", length = 120)
    private String playerATeam;
    @Column(name = "player_a_team_logo", length = 255)
    private String playerATeamLogo;

    @Column(name = "player_b_id", nullable = false)
    private Long playerBId;
    @Column(name = "player_b_name", length = 120)
    private String playerBName;
    @Column(name = "player_b_photo", length = 255)
    private String playerBPhoto;
    @Column(name = "player_b_team", length = 120)
    private String playerBTeam;
    @Column(name = "player_b_team_logo", length = 255)
    private String playerBTeamLogo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private DuelStatus status = DuelStatus.OPEN;

    /** "A" | "B" | "DRAW" | "VOID" (çözülene dek null). */
    @Column(length = 8)
    private String winner;

    @Column(name = "value_a", precision = 8, scale = 2)
    private BigDecimal valueA;

    @Column(name = "value_b", precision = 8, scale = 2)
    private BigDecimal valueB;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

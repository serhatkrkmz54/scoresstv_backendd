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

/**
 * Bir takimin sezon kadrosu. API:
 *   {@code GET /players/squads?team=X}
 *
 * <p>Player master tablo (V19) ile {@code playerId} referans verir; oyuncu
 * adi/foto master tabloda saklanir. Burada sezon-bazli denormalize bilgi
 * (jersey numarasi, pozisyon, sezon basi yasi) tutulur.
 */
@Entity
@Table(
        name = "team_squad",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_team_squad_team_season_player",
                columnNames = {"team_id", "season", "player_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class TeamSquad extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private Integer season;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** "Goalkeeper" / "Defender" / "Midfielder" / "Attacker". */
    @Column(length = 20)
    private String position;

    @Column(name = "jersey_number")
    private Integer jerseyNumber;

    @Column(name = "player_name", nullable = false, length = 255)
    private String playerName;

    @Column(name = "player_age")
    private Integer playerAge;
}

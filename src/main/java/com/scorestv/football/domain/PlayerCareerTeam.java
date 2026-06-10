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

import java.util.List;

/**
 * Bir oyuncunun bir takimda oynadigi tum sezonlarin kaydi. API:
 *   {@code GET /players/teams?player=X}
 *
 * <p>Yanit her takim icin {@code seasons[]} dizisi doner — biz bunu JSONB
 * olarak passthrough sakliyoruz (ornek: {@code [2025, 2024, 2023, 2022]}).
 * UI sezon dropdown'unu doldurur, kullanici secince ilgili sezonu lazy sync.
 *
 * <p>UNIQUE (player_id, team_id) — bir oyuncu ayni takimda birden cok donem
 * oynamis olsa bile tek satir; tum sezonlar JSONB icinde toplanir.
 */
@Entity
@Table(
        name = "player_career_teams",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_player_career_teams",
                columnNames = {"player_id", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class PlayerCareerTeam extends BaseEntity {

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** Yil dizisi: {@code [2025, 2024, 2023, 2022]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Integer> seasons;
}

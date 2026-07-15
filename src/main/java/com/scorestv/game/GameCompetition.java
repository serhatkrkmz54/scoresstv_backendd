package com.scorestv.game;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Bir yarışma dönemi (haftalık/aylık/sezonluk) — altında düellolar. */
@Entity
@Table(name = "game_competition")
@Getter
@Setter
public class GameCompetition extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameScope scope;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 16)
    private String sport = "FOOTBALL";

    private Integer season;

    @Column(name = "league_id")
    private Long leagueId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "lock_at", nullable = false)
    private Instant lockAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameStatus status = GameStatus.DRAFT;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

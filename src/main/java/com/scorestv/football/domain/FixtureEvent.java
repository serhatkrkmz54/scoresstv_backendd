package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bir maçtaki olay (gol, kart, oyuncu değişikliği, VAR). Maç detayı sayfasındaki
 * zaman çizelgesini oluşturur.
 *
 * <p>API-Football olaylar için kalıcı bir ID vermediğinden bu varlık kendi
 * üretilen ID'sini kullanır ({@link BaseEntity}); senkronda bir maçın olayları
 * silinip yeniden yazılır.
 */
@Entity
@Table(name = "fixture_events")
@Getter
@Setter
@NoArgsConstructor
public class FixtureEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    /** Olayın ilgili olduğu takım (opsiyonel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    /** Olayın dakikası. */
    @Column(name = "time_elapsed", nullable = false)
    private Integer timeElapsed;

    /** Uzatma dakikası (örn. 90+3 için 3); yoksa null. */
    @Column(name = "time_extra")
    private Integer timeExtra;

    /** Olay tipi: Goal, Card, subst, Var. */
    @Column(nullable = false, length = 30)
    private String type;

    /** Alt tip: "Normal Goal", "Yellow Card", "Substitution 1"... */
    @Column(length = 60)
    private String detail;

    @Column(length = 120)
    private String comments;

    /** API-Football player id (oyuncu tablosu olmadığından FK değil). */
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 120)
    private String playerName;

    @Column(name = "assist_id")
    private Long assistId;

    @Column(name = "assist_name", length = 120)
    private String assistName;
}

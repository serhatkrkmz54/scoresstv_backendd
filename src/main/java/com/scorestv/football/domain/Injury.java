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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bir maça katılamayacak ya da şüpheli durumdaki oyuncu.
 *
 * <p>{@code type} "Missing Fixture" (oynayamayacak) ya da "Questionable"
 * (şüpheli). {@code reason} sakatlık/disiplin nedeni.
 *
 * <p>Sync replace pattern: o maçın tüm injury satırları silinir, gelenler
 * tam set olarak yazılır.
 */
@Entity
@Table(name = "injuries")
@Getter
@Setter
@NoArgsConstructor
public class Injury {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 120)
    private String playerName;

    @Column(name = "player_photo", length = 255)
    private String playerPhoto;

    /** "Missing Fixture" / "Questionable". */
    @Column(length = 40)
    private String type;

    /** "Broken ankle", "Illness", "Suspended", "Knock"... */
    @Column(length = 120)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

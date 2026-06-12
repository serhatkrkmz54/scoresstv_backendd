package com.scorestv.basketball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Basketbol oyuncu master tablosu.
 *
 * <p>{@link BasketballGamePlayerStat#getPlayer()} FK hedefi. API-Basketball
 * player profil endpoint'i ileride eklenebilir; suanlik sadece "oyuncu
 * statistikleri" sync'i dolduruyor (game stats akiş'inde gorulen id'ler).
 */
@Entity
@Table(name = "basketball_players")
@Getter
@Setter
@NoArgsConstructor
public class BasketballPlayer {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Suanki takim — game stats'tan turetilir, kalici degil. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private BasketballTeam team;

    @UpdateTimestamp
    private Instant updatedAt;
}

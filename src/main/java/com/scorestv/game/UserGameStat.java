package com.scorestv.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Kullanıcı oyun özeti (hızlı sıralama + profil). balance = harcanabilir,
 *  lifetime = sıralama (kazanılan toplam). */
@Entity
@Table(name = "user_game_stat")
@Getter
@Setter
@NoArgsConstructor
public class UserGameStat {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "coin_balance", nullable = false)
    private long coinBalance = 0;

    @Column(name = "lifetime_coins", nullable = false)
    private long lifetimeCoins = 0;

    @Column(name = "total_picks", nullable = false)
    private int totalPicks = 0;

    @Column(name = "correct_picks", nullable = false)
    private int correctPicks = 0;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

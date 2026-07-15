package com.scorestv.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Scores Coin hareket defteri — kaynak-doğru. Her kazanç/harcama bir satır. */
@Entity
@Table(name = "scores_coin_ledger")
@Getter
@Setter
@NoArgsConstructor
public class ScoresCoinLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(name = "ref_type", length = 24)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}

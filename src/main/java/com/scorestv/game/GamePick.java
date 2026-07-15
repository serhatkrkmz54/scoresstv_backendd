package com.scorestv.game;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Kullanıcının bir düelloya verdiği tahmin (giriş zorunlu). Düello başına tek. */
@Entity
@Table(name = "game_pick",
        uniqueConstraints = @UniqueConstraint(name = "uq_game_pick",
                columnNames = {"duel_id", "user_id"}))
@Getter
@Setter
public class GamePick extends BaseEntity {

    @Column(name = "competition_id", nullable = false)
    private Long competitionId;

    @Column(name = "duel_id", nullable = false)
    private Long duelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** "A" | "B". */
    @Column(nullable = false, length = 4)
    private String pick;

    /** Çözülene dek null. */
    private Boolean correct;

    @Column(name = "coins_awarded", nullable = false)
    private int coinsAwarded = 0;
}

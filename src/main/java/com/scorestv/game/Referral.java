package com.scorestv.game;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Bir davet kaydi — {@code referrerId} kullanicisinin davet kodunu, yeni
 * {@code refereeId} kullanicisi kullandi. Yeni kullanici basina TEK kayit
 * (uq_referrals_referee) → cift odul engellenir.
 */
@Entity
@Table(name = "referrals",
        uniqueConstraints = @UniqueConstraint(name = "uq_referrals_referee",
                columnNames = "referee_id"))
@Getter
@Setter
public class Referral extends BaseEntity {

    @Column(name = "referrer_id", nullable = false)
    private Long referrerId;

    @Column(name = "referee_id", nullable = false)
    private Long refereeId;

    @Column(name = "reward_each", nullable = false)
    private int rewardEach;
}

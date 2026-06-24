package com.scorestv.volleyball.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.mobile.domain.MobileDeviceToken;
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
 * Bir cihazin favori bir VOLEYBOL macina abone oldugu kayit (basketbol
 * {@code DeviceBasketballSubscription}'in voleybol esi — ayri tablo, ayri FK).
 *
 * <p>Mac basladi / set bitti (skorlu) / mac bitti FCM push'lari bu tablodan
 * recipient cikarir. Replace-sync.
 */
@Entity
@Table(
        name = "device_volleyball_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_volleyball_subscriptions",
                columnNames = {"device_token_id", "volleyball_game_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class DeviceVolleyballSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "volleyball_game_id", nullable = false)
    private VolleyballGame game;
}

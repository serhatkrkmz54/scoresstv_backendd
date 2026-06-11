package com.scorestv.basketball.domain;

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
 * Bir cihazın favori bir BASKETBOL maçına abone olduğu kayıt (football'daki
 * {@link com.scorestv.mobile.domain.DeviceMatchSubscription}'ın basketbol
 * karşılığı — ayrı tablo, ayrı FK).
 *
 * <p>Maç başladı / çeyrek bitti (skorlu) / maç bitti FCM push'ları bu tablodan
 * recipient çıkarır. Replace-sync: cihaz favori listesini her değiştirdiğinde
 * tüm güncel listeyi POST eder; backend eskiyi silip yeniyi yazar.
 */
@Entity
@Table(
        name = "device_basketball_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_basketball_subscriptions",
                columnNames = {"device_token_id", "basketball_game_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class DeviceBasketballSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "basketball_game_id", nullable = false)
    private BasketballGame game;
}

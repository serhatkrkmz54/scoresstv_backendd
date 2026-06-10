package com.scorestv.mobile.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.Fixture;
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
 * Bir cihazin favori bir maca abone oldugu kayit.
 *
 * <p>Mobile tarafta favori maclar SharedPreferences'a lokal yazilir; bildirim
 * gelmesi icin backend de bunu bilmek zorunda. {@code mac-bazli bildirim}
 * altyapisinin temeli — uygulama kapaliyken FCM push'lari bu tablodan
 * recipient cikartir.
 *
 * <p><b>Replace sync pattern:</b> mobile favori listesini her degistirdiginde
 * tum guncel listeyi POST eder; backend bu cihaz icin eski abonelikleri
 * silip yenilerini yazar (basit, idempotent).
 *
 * <p><b>Default bildirim seti:</b> favori mac = "tum onemli olaylar" (gol,
 * kart, penalti, basladi, bitti). Mac-bazli ayrintili toggle yok — basit
 * tutmak icin. Ileride eklenebilir.
 */
@Entity
@Table(
        name = "device_match_subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_match_subscriptions",
                columnNames = {"device_token_id", "fixture_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class DeviceMatchSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;
}

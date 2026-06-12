package com.scorestv.mobile.domain;

import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
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
 * Bir cihaz icin bir BASKETBOL takiminin bildirim tercihleri.
 *
 * <p>Futbol {@link UserNotificationPref} ile aynı pattern, basketbola adapte:
 * <ul>
 *   <li>5 olay tipi yerine 3 (basketbolda gol/kart/penalti yok)</li>
 *   <li>FK BasketballTeam'e</li>
 * </ul>
 *
 * <p>(device_token, team) çifti benzersizdir; mobile bir takım için tek satır
 * tutar.
 *
 * <p>Bool kolonlar: 3 olay tipi. Mobile JSON key'leri ile mapping:
 * <ul>
 *   <li>{@code basladi} → {@code notifyStart}  — tip-off / Q1 başladı</li>
 *   <li>{@code ceyrek}  → {@code notifyPeriod} — Q1/Q2/Q3 sonu + HT</li>
 *   <li>{@code bitti}   → {@code notifyFinal}  — FT / AOT</li>
 * </ul>
 */
@Entity
@Table(
        name = "basketball_notification_prefs",
        uniqueConstraints = @UniqueConstraint(
                name = "basketball_notification_prefs_device_token_id_team_id_key",
                columnNames = {"device_token_id", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballNotificationPref extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private BasketballTeam team;

    @Column(name = "notify_start", nullable = false)
    private boolean notifyStart = true;

    @Column(name = "notify_period", nullable = false)
    private boolean notifyPeriod = true;

    @Column(name = "notify_final", nullable = false)
    private boolean notifyFinal = true;
}

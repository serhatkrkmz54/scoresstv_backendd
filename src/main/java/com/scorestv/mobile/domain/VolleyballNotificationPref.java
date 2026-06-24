package com.scorestv.mobile.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.volleyball.domain.VolleyballTeam;
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
 * Bir cihaz icin bir VOLEYBOL takiminin bildirim tercihleri.
 *
 * <p>Basketbol {@link BasketballNotificationPref} ile ayni pattern, 3 olay:
 * <ul>
 *   <li>{@code basladi} → {@code notifyStart}  — mac basladi (S1)</li>
 *   <li>{@code set}     → {@code notifyPeriod} — set bitti</li>
 *   <li>{@code bitti}   → {@code notifyFinal}  — FT / AW</li>
 * </ul>
 */
@Entity
@Table(
        name = "volleyball_notification_prefs",
        uniqueConstraints = @UniqueConstraint(
                name = "volleyball_notification_prefs_device_token_id_team_id_key",
                columnNames = {"device_token_id", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class VolleyballNotificationPref extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private VolleyballTeam team;

    @Column(name = "notify_start", nullable = false)
    private boolean notifyStart = true;

    @Column(name = "notify_period", nullable = false)
    private boolean notifyPeriod = true;

    @Column(name = "notify_final", nullable = false)
    private boolean notifyFinal = true;
}

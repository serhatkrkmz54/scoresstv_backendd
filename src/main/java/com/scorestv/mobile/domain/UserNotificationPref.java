package com.scorestv.mobile.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.Team;
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
 * Bir cihaz icin bir takimin bildirim tercihleri.
 *
 * <p>(device_token, team) cifti benzersizdir; mobile bir takim icin tek satir
 * tutar.
 *
 * <p>Bool kolonlar: 5 olay tipi. Mobile JSON key'leri ile mapping:
 * <ul>
 *   <li>{@code gol}     → {@code notifyGoal}</li>
 *   <li>{@code kirmizi} → {@code notifyRedCard}</li>
 *   <li>{@code penalti} → {@code notifyPenalty}</li>
 *   <li>{@code basladi} → {@code notifyKickoff}</li>
 *   <li>{@code bitti}   → {@code notifyFinal}</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_notification_prefs",
        uniqueConstraints = @UniqueConstraint(
                name = "user_notification_prefs_device_token_id_team_id_key",
                columnNames = {"device_token_id", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationPref extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_token_id", nullable = false)
    private MobileDeviceToken deviceToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "notify_goal", nullable = false)
    private boolean notifyGoal = true;

    @Column(name = "notify_red_card", nullable = false)
    private boolean notifyRedCard = true;

    @Column(name = "notify_penalty", nullable = false)
    private boolean notifyPenalty = true;

    @Column(name = "notify_kickoff", nullable = false)
    private boolean notifyKickoff = true;

    @Column(name = "notify_final", nullable = false)
    private boolean notifyFinal = true;

    /** İlk 11 (kadro) açıklandığında bildirim — kickoff'tan ~20-40 dk önce. */
    @Column(name = "notify_lineup", nullable = false)
    private boolean notifyLineup = true;

    /** "İlk yarı bitti" (devre arası) bildirimi. */
    @Column(name = "notify_halftime", nullable = false)
    private boolean notifyHalftime = true;

    /** "İkinci yarı başladı" bildirimi. */
    @Column(name = "notify_second_half", nullable = false)
    private boolean notifySecondHalf = true;
}

package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link VolleyballNotificationPref} CRUD ve lookup.
 *
 * <p>Basketbol {@link BasketballNotificationPrefRepository} ile ayni pattern,
 * voleybol icin 3 olay tipi recipient sorgusu.
 */
public interface VolleyballNotificationPrefRepository
        extends JpaRepository<VolleyballNotificationPref, Long> {

    /** Bir cihazin tum voleybol takim tercihleri — mobile sync GET icin. */
    List<VolleyballNotificationPref> findByDeviceTokenId(Long deviceTokenId);

    /** Spesifik cihaz+takim kaydi — upsert lookup. */
    Optional<VolleyballNotificationPref> findByDeviceTokenIdAndTeamId(
            Long deviceTokenId, Long teamId);

    /** Bir cihazin tum prefs'lerini sil. */
    void deleteByDeviceTokenId(Long deviceTokenId);

    @Query("SELECT p FROM VolleyballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyStart = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<VolleyballNotificationPref> findRecipientsForStart(
            @Param("teamId") Long teamId);

    @Query("SELECT p FROM VolleyballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyPeriod = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<VolleyballNotificationPref> findRecipientsForPeriod(
            @Param("teamId") Long teamId);

    @Query("SELECT p FROM VolleyballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyFinal = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<VolleyballNotificationPref> findRecipientsForFinal(
            @Param("teamId") Long teamId);
}

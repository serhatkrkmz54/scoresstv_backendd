package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link BasketballNotificationPref} CRUD ve lookup.
 *
 * <p>Futbol {@link UserNotificationPrefRepository} ile aynı pattern, basketbol
 * için 3 olay tipi recipient sorgusu.
 */
public interface BasketballNotificationPrefRepository
        extends JpaRepository<BasketballNotificationPref, Long> {

    /** Bir cihazın tüm basketbol takım tercihleri — mobile sync GET için. */
    List<BasketballNotificationPref> findByDeviceTokenId(Long deviceTokenId);

    /** Spesifik cihaz+takım kaydı — upsert lookup. */
    Optional<BasketballNotificationPref> findByDeviceTokenIdAndTeamId(
            Long deviceTokenId, Long teamId);

    /** Bir cihazın tüm prefs'lerini sil — mobile takım listesini sıfırlarsa. */
    void deleteByDeviceTokenId(Long deviceTokenId);

    /**
     * Bir basketbol takımı için bildirim isteyen tüm cihazların prefs+token'ları.
     * BasketballNotificationService dispatch öncesi bunu kullanır.
     */
    @Query("SELECT p FROM BasketballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyStart = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<BasketballNotificationPref> findRecipientsForStart(
            @Param("teamId") Long teamId);

    @Query("SELECT p FROM BasketballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyPeriod = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<BasketballNotificationPref> findRecipientsForPeriod(
            @Param("teamId") Long teamId);

    @Query("SELECT p FROM BasketballNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyFinal = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<BasketballNotificationPref> findRecipientsForFinal(
            @Param("teamId") Long teamId);
}

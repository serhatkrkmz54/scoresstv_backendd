package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** {@link UserNotificationPref} CRUD ve lookup. */
public interface UserNotificationPrefRepository
        extends JpaRepository<UserNotificationPref, Long> {

    /** Bir cihazin tum takim tercihleri — mobile sync GET icin. */
    List<UserNotificationPref> findByDeviceTokenId(Long deviceTokenId);

    /** Spesifik cihaz+takim kaydi — upsert lookup. */
    Optional<UserNotificationPref> findByDeviceTokenIdAndTeamId(
            Long deviceTokenId, Long teamId);

    /** Bir cihazin tum prefs'lerini sil — mobile takim listesini sifirlarsa. */
    void deleteByDeviceTokenId(Long deviceTokenId);

    /**
     * Bir takim icin bildirim isteyen tum cihazlarin token'lari.
     * Notification dispatcher dispatch oncesi bu sorguyu calistirir.
     *
     * <p>{@code eventCol} olay turune gore filtre kolonunu degistirmek icin
     * her olay icin ayri metod var (asagida).
     */
    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyGoal = true")
    List<UserNotificationPref> findRecipientsForGoal(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyRedCard = true")
    List<UserNotificationPref> findRecipientsForRedCard(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyPenalty = true")
    List<UserNotificationPref> findRecipientsForPenalty(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyKickoff = true")
    List<UserNotificationPref> findRecipientsForKickoff(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyFinal = true")
    List<UserNotificationPref> findRecipientsForFinal(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyLineup = true")
    List<UserNotificationPref> findRecipientsForLineup(@Param("teamId") Long teamId);
}

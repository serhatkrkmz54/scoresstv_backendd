package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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
            + "WHERE p.team.id = :teamId AND p.notifyGoal = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForGoal(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyRedCard = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForRedCard(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyPenalty = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForPenalty(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyKickoff = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForKickoff(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyFinal = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForFinal(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyLineup = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForLineup(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyHalftime = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForHalftime(@Param("teamId") Long teamId);

    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifySecondHalf = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRecipientsForSecondHalf(@Param("teamId") Long teamId);

    /**
     * UEFA Kulup siralamasi degisince bu takimi takip eden (notify_rankings_club
     * acik) cihazlar. RankingNotificationService kullanir.
     */
    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken "
            + "WHERE p.team.id = :teamId AND p.notifyRankingsClub = true "
            + "AND p.deviceToken.notificationsEnabled = true")
    List<UserNotificationPref> findRankingClubRecipients(@Param("teamId") Long teamId);

    /**
     * Haber (news) FAVORITES-hedefi alicilari: verilen takim id kumesinden
     * herhangi birini takip eden, master bildirim + haber toggle acik ve
     * haberin diliyle (locale on eki) eslesen cihazlarin tercih kayitlari.
     *
     * <p>Bir cihaz birden cok takimi takip ediyorsa birden cok satir donebilir;
     * caller token'lari {@code Set} ile tekillestirir. Bos {@code teamIds} icin
     * cagrilmamalidir (bos IN sorgusu).
     */
    @Query("SELECT p FROM UserNotificationPref p "
            + "JOIN FETCH p.deviceToken d "
            + "WHERE p.team.id IN :teamIds "
            + "AND d.notificationsEnabled = true "
            + "AND d.notifyNews = true "
            + "AND LOWER(d.locale) LIKE LOWER(CONCAT(:lang, '%'))")
    List<UserNotificationPref> findNewsFavoriteRecipients(
            @Param("teamIds") Collection<Long> teamIds,
            @Param("lang") String lang);
}

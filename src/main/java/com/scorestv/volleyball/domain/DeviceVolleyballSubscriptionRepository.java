package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link DeviceVolleyballSubscription} CRUD + dispatcher lookup.
 *
 * <p>{@link #findRecipientsForGame(Long)} bir voleybol maci icin abone olan
 * tum cihazlari (device token JOIN FETCH ile) doner.
 */
public interface DeviceVolleyballSubscriptionRepository
        extends JpaRepository<DeviceVolleyballSubscription, Long> {

    List<DeviceVolleyballSubscription> findByDeviceTokenId(Long deviceTokenId);

    /** Replace-sync: yeni listeyi yazmadan once cihazin eski tumunu siler. */
    @Modifying
    @Query("DELETE FROM DeviceVolleyballSubscription s "
            + "WHERE s.deviceToken.id = :deviceTokenId")
    void deleteByDeviceTokenId(@Param("deviceTokenId") Long deviceTokenId);

    /** <b>Dispatcher anahtar sorgusu</b> — macin favori abonesi tum cihazlar. */
    @Query("SELECT s FROM DeviceVolleyballSubscription s "
            + "JOIN FETCH s.deviceToken "
            + "WHERE s.game.id = :gameId "
            + "AND s.deviceToken.notificationsEnabled = true")
    List<DeviceVolleyballSubscription> findRecipientsForGame(
            @Param("gameId") Long gameId);
}

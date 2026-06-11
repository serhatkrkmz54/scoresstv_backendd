package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link DeviceBasketballSubscription} CRUD + dispatcher lookup.
 *
 * <p>{@link #findRecipientsForGame(Long)} bir basketbol maçı için abone olan
 * tüm cihazları (device token JOIN FETCH ile) döner — başladı/çeyrek/bitti
 * push dispatch'i bu listeyi kullanır.
 */
public interface DeviceBasketballSubscriptionRepository
        extends JpaRepository<DeviceBasketballSubscription, Long> {

    List<DeviceBasketballSubscription> findByDeviceTokenId(Long deviceTokenId);

    /** Replace-sync: yeni listeyi yazmadan önce cihazın eski tümünü siler. */
    @Modifying
    @Query("DELETE FROM DeviceBasketballSubscription s "
            + "WHERE s.deviceToken.id = :deviceTokenId")
    void deleteByDeviceTokenId(@Param("deviceTokenId") Long deviceTokenId);

    /** <b>Dispatcher anahtar sorgusu</b> — maçın favori abonesi tüm cihazlar. */
    @Query("SELECT s FROM DeviceBasketballSubscription s "
            + "JOIN FETCH s.deviceToken "
            + "WHERE s.game.id = :gameId")
    List<DeviceBasketballSubscription> findRecipientsForGame(
            @Param("gameId") Long gameId);
}

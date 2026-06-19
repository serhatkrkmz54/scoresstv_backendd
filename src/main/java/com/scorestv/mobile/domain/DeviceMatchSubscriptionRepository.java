package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link DeviceMatchSubscription} CRUD ve dispatcher lookup.
 *
 * <h3>NotificationDispatcher entegrasyonu</h3>
 * {@link #findRecipientsForFixture(Long)} bir mac icin abone olan tum
 * cihazlari (device token JOIN FETCH ile) doner — gol/kart/MB/MS dispatch'i
 * bu listeyi takim-bazli prefs alicilarina ekler.
 */
public interface DeviceMatchSubscriptionRepository
        extends JpaRepository<DeviceMatchSubscription, Long> {

    /**
     * Bir cihazin tum favori mac abonelikleri — replace sync sirasinda
     * eski set'i goruntulemek veya silmek icin.
     */
    List<DeviceMatchSubscription> findByDeviceTokenId(Long deviceTokenId);

    /**
     * Bir cihazin tum favori abonelikleri (replace pattern: yeni listeyi
     * yazmadan once siler).
     */
    @Modifying
    @Query("DELETE FROM DeviceMatchSubscription s "
            + "WHERE s.deviceToken.id = :deviceTokenId")
    void deleteByDeviceTokenId(@Param("deviceTokenId") Long deviceTokenId);

    /**
     * <b>Dispatcher anahtar sorgusu</b> — verilen mac icin favori abonesi
     * olan tum cihazlar (device token JOIN FETCH).
     *
     * <p>{@link com.scorestv.mobile.notify.NotificationDispatcherService}
     * event/kickoff/final dispatch'i sirasinda bu listeyi recipient set'ine
     * ekler. Default 5 event tipi de aktif sayilir (mac-bazli toggle yok).
     */
    @Query("SELECT s FROM DeviceMatchSubscription s "
            + "JOIN FETCH s.deviceToken "
            + "WHERE s.fixture.id = :fixtureId "
            + "AND s.deviceToken.notificationsEnabled = true")
    List<DeviceMatchSubscription> findRecipientsForFixture(
            @Param("fixtureId") Long fixtureId);
}

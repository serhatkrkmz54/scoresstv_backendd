package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@link MobileDeviceToken} CRUD ve lookup. */
public interface MobileDeviceTokenRepository
        extends JpaRepository<MobileDeviceToken, Long> {

    /** FCM token unique — POST register/update lookup'ı icin. */
    Optional<MobileDeviceToken> findByFcmToken(String fcmToken);

    void deleteByFcmToken(String fcmToken);
}

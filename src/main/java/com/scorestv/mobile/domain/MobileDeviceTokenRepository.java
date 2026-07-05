package com.scorestv.mobile.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** {@link MobileDeviceToken} CRUD ve lookup. */
public interface MobileDeviceTokenRepository
        extends JpaRepository<MobileDeviceToken, Long> {

    /** FCM token unique — POST register/update lookup'ı icin. */
    Optional<MobileDeviceToken> findByFcmToken(String fcmToken);

    void deleteByFcmToken(String fcmToken);

    /**
     * Belirli bir ulke kodu icin FIFA / UEFA Ulke siralama bildirimi alacak
     * aktif cihazlar. Filtreler: ulke eslesir (buyuk/kucuk harf duyarsiz),
     * master bildirim acik, siralama-ulke toggle acik.
     */
    @Query("""
            SELECT t FROM MobileDeviceToken t
            WHERE UPPER(t.countryCode) = UPPER(:countryCode)
              AND t.notificationsEnabled = true
              AND t.notifyRankingsCountry = true
            """)
    List<MobileDeviceToken> findRankingsCountryRecipients(
            @Param("countryCode") String countryCode);

    /**
     * Haber (news) ALL-hedefi alicilari: haberin diliyle (locale) eslesen,
     * master bildirim + haber toggle acik tum cihazlar. locale buyuk/kucuk
     * harf duyarsiz eslenir (cihaz "TR"/"tr", "en-US" gibi degerler tutabilir;
     * on ek kontrolu icin LIKE :lang%).
     */
    @Query("""
            SELECT t FROM MobileDeviceToken t
            WHERE LOWER(t.locale) LIKE LOWER(CONCAT(:lang, '%'))
              AND t.notificationsEnabled = true
              AND t.notifyNews = true
            """)
    List<MobileDeviceToken> findNewsRecipientsByLang(@Param("lang") String lang);
}

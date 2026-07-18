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
     * Belirli bir kullanicinin, master bildirimi acik tum cihazlari.
     * Kullaniciya ozel push (oyun sonucu: "3/5 tahminin tuttu, +820 puan")
     * icin hedefleme. app_user_id register aninda JWT'den doldurulur.
     */
    List<MobileDeviceToken> findByAppUserIdAndNotificationsEnabledTrue(Long appUserId);

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

    /**
     * Genel (broadcast) bildirim alicilari. Yalnizca master
     * {@code notificationsEnabled} acik cihazlar. Opsiyonel filtreler:
     *   platform null → tum platformlar; degilse "ios"/"android" ile eslesir.
     *   lang null      → tum diller; degilse locale on eki (LIKE :lang%) ile eslesir.
     */
    @Query("""
            SELECT t FROM MobileDeviceToken t
            WHERE t.notificationsEnabled = true
              AND (:platform IS NULL OR LOWER(t.platform) = :platform)
              AND (:lang IS NULL OR LOWER(t.locale) LIKE LOWER(CONCAT(:lang, '%')))
            """)
    List<MobileDeviceToken> findBroadcastRecipients(@Param("platform") String platform,
                                                    @Param("lang") String lang);

    /** {@link #findBroadcastRecipients} ile ayni filtre — sadece SAYI (hizli, enqueue aninda). */
    @Query("""
            SELECT COUNT(t) FROM MobileDeviceToken t
            WHERE t.notificationsEnabled = true
              AND (:platform IS NULL OR LOWER(t.platform) = :platform)
              AND (:lang IS NULL OR LOWER(t.locale) LIKE LOWER(CONCAT(:lang, '%')))
            """)
    long countBroadcastRecipients(@Param("platform") String platform,
                                  @Param("lang") String lang);
}

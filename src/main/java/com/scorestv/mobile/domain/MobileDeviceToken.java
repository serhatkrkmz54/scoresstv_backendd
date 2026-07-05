package com.scorestv.mobile.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mobile uygulama kuran bir cihazi temsil eden FCM token kaydi.
 *
 * <p>Anonim cihaz tabanli model — kullanici hesabi yok, fcm_token unique
 * cihaz identity. Mobile uygulama her acilista (veya token degisirse)
 * POST /api/v1/mobile/device-tokens cagirir; backend INSERT/UPDATE eder.
 *
 * <p>FCM gonderim katmani notification dispatcher buradaki token'lari kullanir.
 */
@Entity
@Table(name = "mobile_device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class MobileDeviceToken extends BaseEntity {

    @Column(name = "fcm_token", nullable = false, unique = true, columnDefinition = "TEXT")
    private String fcmToken;

    /** "android" | "ios". Web'de FCM web push token yok, web ayri olur. */
    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    /** Bildirim icerigini bu dilde olusturmak icin. Default "tr". */
    @Column(nullable = false, length = 10)
    private String locale = "tr";

    /** Cihaz son ne zaman aktif oldu (her POST cagrisinda guncellenir). */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /**
     * Master "Tum bildirimleri kapat" toggle. Default TRUE (acik).
     * Kullanici Profil > "Bildirimler" satirindan kapatinca PATCH
     * {@code /api/v1/mobile/device-tokens/notifications-enabled} ile FALSE
     * yapilir. NotificationDispatcher push gondermeden once filter eder.
     */
    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    /**
     * Cihazin ulke kodu — ISO-3 / futbol federasyon kodu (orn. "TUR",
     * "ENG", "ITA"). FIFA + UEFA Ulke siralamasi bildirimleri bu koda gore
     * hedeflenir. Mobile profil ulkesinden (yoksa locale'den) turetip POST
     * eder; null ise siralama bildirimi gonderilmez. FIFA ve UEFA ayni
     * 3-harfli kodu kullandigi icin eslesme tutarlidir.
     */
    @Column(name = "country_code", length = 10)
    private String countryCode;

    /**
     * FIFA + UEFA Ulke siralama bildirimleri toggle. Default TRUE (acik).
     * Mobile Ayarlar'dan PATCH
     * {@code /api/v1/mobile/device-tokens/rankings-country-enabled} ile
     * degistirilir. Ulkeye gore siralama bildirimi gonderiminde filtre.
     */
    @Column(name = "notify_rankings_country", nullable = false)
    private boolean notifyRankingsCountry = true;

    /**
     * Haber (news) push bildirimleri toggle. Default TRUE (acik). Editor bir
     * haberi yayinlarken push gonderirse, bu kolon acik + master bildirim acik
     * cihazlar (dil eslesirse) alir. Mobil "Haber bildirimleri" ayari sonraki
     * fazda bu kolonu degistirir.
     */
    @Column(name = "notify_news", nullable = false)
    private boolean notifyNews = true;
}

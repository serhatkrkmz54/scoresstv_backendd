package com.scorestv.basketball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Basketbol takımı (API-Basketball team id = PK). Football'dan ayrı tablo. */
@Entity
@Table(name = "basketball_teams")
@Getter
@Setter
@NoArgsConstructor
public class BasketballTeam {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Türkçe ad (manuel/editör girişi). Sync ASLA dokunmaz; TR locale'de
     *  gösterilir, boşsa name'e düşülür. */
    @Column(length = 160)
    private String nameTr;

    /** API logo URL'i (aynalanacak kaynak). */
    @Column(length = 400)
    private String logo;

    /** MinIO/CDN nesne anahtarı — aynalandıysa dolar; serving CDN URL'i bundan kurar. */
    @Column(length = 255)
    private String logoKey;

    // ------------------------------------------------------------------
    // Profile genisletmeleri (V54) — /teams?id=X yanitindan dolar
    // ------------------------------------------------------------------

    /** Takimin ulkesi (API: country.name). */
    @Column(name = "country_name", length = 120)
    private String countryName;

    /** Ulke ISO-2 kodu. */
    @Column(name = "country_code", length = 8)
    private String countryCode;

    /** Ulke bayragi (URL — RemoteAssetImage uyumlu). */
    @Column(name = "country_flag", length = 400)
    private String countryFlag;

    /** Takim kodu (orn. "FBA"). */
    @Column(length = 16)
    private String code;

    /** Milli takim mi (FIBA milli takimlari icin true). */
    @Column(nullable = false)
    private boolean national = false;

    /** Kurulus yili. */
    private Integer founded;

    /** Ev sahasinin adi (API: country/venue.name). */
    @Column(name = "venue_name", length = 200)
    private String venueName;

    @Column(name = "venue_city", length = 120)
    private String venueCity;

    @Column(name = "venue_capacity")
    private Integer venueCapacity;

    /** URL-dostu slug — SlugUtil.teamSlug(name, id) ile uretilir. */
    @Column(length = 180)
    private String slug;

    /** Covered olunca DailyBasketballTeamRefreshJob gunluk tazeler. */
    @Column(nullable = false)
    private boolean covered = false;

    /** Profil senkronu son ne zaman cagrildi (freshness gate). */
    @Column(name = "last_profile_synced_at")
    private Instant lastProfileSyncedAt;

    /** Statistics senkronu son ne zaman cagrildi (freshness gate). */
    @Column(name = "last_stats_synced_at")
    private Instant lastStatsSyncedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

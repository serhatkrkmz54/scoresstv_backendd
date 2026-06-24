package com.scorestv.volleyball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Voleybol takimi (API-Volleyball team id = PK). Ayri tablo. */
@Entity
@Table(name = "volleyball_teams")
@Getter
@Setter
@NoArgsConstructor
public class VolleyballTeam {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Turkce ad (manuel/editor girisi). Sync ASLA dokunmaz; bossa name'e duser. */
    @Column(length = 160)
    private String nameTr;

    /** API logo URL'i (aynalanacak kaynak). */
    @Column(length = 400)
    private String logo;

    /** MinIO/CDN nesne anahtari — aynalandiysa dolar. */
    @Column(length = 255)
    private String logoKey;

    /** Takimin ulkesi (API: country.name). */
    @Column(name = "country_name", length = 120)
    private String countryName;

    /** Ulke ISO-2 kodu. */
    @Column(name = "country_code", length = 8)
    private String countryCode;

    /** Ulke bayragi (URL). */
    @Column(name = "country_flag", length = 400)
    private String countryFlag;

    /** Milli takim mi. */
    @Column(nullable = false)
    private boolean national = false;

    /** URL-dostu slug. */
    @Column(length = 180)
    private String slug;

    /** Covered olunca daily refresh job gunluk tazeler. */
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

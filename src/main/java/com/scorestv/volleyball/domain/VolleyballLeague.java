package com.scorestv.volleyball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Voleybol ligi (API-Volleyball league id = PK). Ayri tablo. */
@Entity
@Table(name = "volleyball_leagues")
@Getter
@Setter
@NoArgsConstructor
public class VolleyballLeague {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Turkce lig adi (manuel). Sync dokunmaz; TR locale'de gosterilir. */
    @Column(length = 160)
    private String nameTr;

    @Column(length = 40)
    private String type;

    @Column(length = 400)
    private String logo;

    /** Lig logosu MinIO/CDN anahtari (aynalandiysa). */
    @Column(length = 255)
    private String logoKey;

    @Column(length = 120)
    private String countryName;

    /** Turkce ulke adi (manuel). Sync dokunmaz. */
    @Column(length = 120)
    private String countryNameTr;

    @Column(length = 10)
    private String countryCode;

    @Column(length = 400)
    private String countryFlag;

    /** Ulke bayragi MinIO/CDN anahtari (aynalandiysa). */
    @Column(length = 255)
    private String countryFlagKey;

    /** Guncel sezon (orn. "2024") — /leagues seed'inden gelir. */
    @Column(length = 20)
    private String currentSeason;

    /** URL-friendly slug. Detay sayfasinda ID cozumu icin. */
    @Column(length = 180)
    private String slug;

    /** API-Volleyball /leagues endpoint'inden gelen tum sezonlar listesi (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seasons_json", columnDefinition = "jsonb")
    private String seasonsJson;

    /** Bu lig "covered" mi — covered ligler periyodik refresh job'larindan gecer. */
    @Column(nullable = false)
    private boolean covered = false;

    /** /leagues?id=X full info sync zamani — lazy refresh freshness gate. */
    @Column(name = "last_info_synced_at")
    private Instant lastInfoSyncedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

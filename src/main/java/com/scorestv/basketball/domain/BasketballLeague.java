package com.scorestv.basketball.domain;

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

/** Basketbol ligi (API-Basketball league id = PK). Football'dan ayrı tablo. */
@Entity
@Table(name = "basketball_leagues")
@Getter
@Setter
@NoArgsConstructor
public class BasketballLeague {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Türkçe lig adı (manuel). Sync dokunmaz; TR locale'de gösterilir. */
    @Column(length = 160)
    private String nameTr;

    @Column(length = 40)
    private String type;

    @Column(length = 400)
    private String logo;

    /** Lig logosu MinIO/CDN anahtarı (aynalandıysa). */
    @Column(length = 255)
    private String logoKey;

    @Column(length = 120)
    private String countryName;

    /** Türkçe ülke adı (manuel). Sync dokunmaz. */
    @Column(length = 120)
    private String countryNameTr;

    @Column(length = 10)
    private String countryCode;

    @Column(length = 400)
    private String countryFlag;

    /** Ülke bayrağı MinIO/CDN anahtarı (aynalandıysa). */
    @Column(length = 255)
    private String countryFlagKey;

    /** Güncel sezon (örn. "2025-2026") — /leagues seed'inden gelir. Teams/
     *  standings çekmek için referans. games sync'i bu alana dokunmaz. */
    @Column(length = 20)
    private String currentSeason;

    /** URL-friendly slug (örn. "nba", "euroleague-2025"). Mobile/web detay
     *  sayfasında ID çözümü için kullanılır. Unique. */
    @Column(length = 180)
    private String slug;

    /**
     * API-Basketball /leagues endpoint'inden gelen tüm sezonlar listesi.
     * JSONB olarak tutulur — her sezon için season string, start/end date,
     * coverage flag'leri (games, standings, players). Detay sayfasındaki
     * sezon dropdown'ı bunu okur.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seasons_json", columnDefinition = "jsonb")
    private String seasonsJson;

    /**
     * Bu lig "covered" mı — covered ligler periyodik refresh job'larından
     * geçer (günlük lig info + top players). Futbolda olduğu gibi sadece
     * popüler liglerle kotayı paylaşır.
     */
    @Column(nullable = false)
    private boolean covered = false;

    /** /leagues?id=X full info sync zamanı — lazy refresh freshness gate. */
    @Column(name = "last_info_synced_at")
    private Instant lastInfoSyncedAt;

    /** Top players (scorers/rebounders/assists) sync zamanı — günlük cron
     *  ve lazy detay sayfası açılışında 1 saat freshness ile gate edilir. */
    @Column(name = "last_top_players_synced_at")
    private Instant lastTopPlayersSyncedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

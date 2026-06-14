package com.scorestv.basketball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Basketbol oyuncu master tablosu.
 *
 * <p>{@code BasketballGamePlayerStat#getPlayer()} FK hedefi. Iki kaynaktan
 * beslenir:
 * <ul>
 *   <li><b>Game stats sync</b> — iskelet (sadece id + name). Mac istatistik
 *       senkronu sirasinda yeni gorulen oyuncular minimal eklenir.
 *   <li><b>Profile sync</b> ({@code BasketballPlayerProfileSyncService}) —
 *       API-Basketball {@code /players?id=X&season=Y} endpoint'inden full
 *       profile (foto, ulke, dogum, fiziksel ozellikler). Iskelet kayit
 *       guncellenir, foto MinIO'ya mirror'lanir.
 * </ul>
 */
@Entity
@Table(name = "basketball_players")
@Getter
@Setter
@NoArgsConstructor
public class BasketballPlayer {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** API'den ayri gelirse — UI'da "Soyad ad" gibi gostermek icin. */
    @Column(length = 120)
    private String firstName;

    @Column(length = 120)
    private String lastName;

    /** URL-friendly slug (orn. "lebron-james-237"). Player detay sayfasi
     *  icin. Profile sync'inde olusturulur. */
    @Column(length = 180)
    private String slug;

    /** Suanki / son bilinen takim — game stats'tan turetilir, kalici. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private BasketballTeam team;

    // ---- Profil bilgileri (API /players profile sync'inden gelir) ----
    @Column(length = 400)
    private String photo;

    /** Foto MinIO/CDN anahtari (aynalandiysa). */
    @Column(length = 255)
    private String photoKey;

    @Column
    private LocalDate birthDate;

    @Column(length = 160)
    private String birthPlace;

    @Column(length = 120)
    private String birthCountry;

    /** Uyrugu (ulke adi). */
    @Column(length = 120)
    private String nationality;

    /** Boy (cm). */
    @Column
    private Integer heightCm;

    /** Kilo (kg). */
    @Column
    private Integer weightKg;

    /** Forma numarasi (sezon basina degisebilir; en son bilinen). */
    @Column
    private Integer jerseyNumber;

    /** Pozisyon (Guard / Forward / Center / vs.). */
    @Column(length = 40)
    private String position;

    /** Kolej (NCAA arsivi — opsiyonel). */
    @Column(length = 200)
    private String college;

    /** /players?id=X sync zamani — lazy refresh freshness gate. */
    @Column(name = "last_profile_synced_at")
    private Instant lastProfileSyncedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

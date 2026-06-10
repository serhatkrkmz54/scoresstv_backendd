package com.scorestv.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Oyuncu master kayit. API-Football player_id'si PK olarak kullanilir.
 *
 * <p>Aynalama: {@code photo_url} API'den gelir; {@link
 * com.scorestv.football.image.ImageMirrorService#mirrorPlayerPhotos()} bunu
 * MinIO'ya indirip {@code photo_key}'i doldurur. Diger tablolarda
 * (injuries, league_top_players, fixture_player_stats, vb.) {@code player_id}
 * referans kalir; ama UI {@code photo_key} mevcut ise CDN URL'sini, yoksa
 * orijinal {@code photo_url}'i kullanir.
 */
@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    /** API-Football player id (atanmış; üretilmez). */
    @Id
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /** MinIO'da aynalanan fotonun nesne anahtarı; aynalanana dek null. */
    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(length = 120)
    private String firstname;

    @Column(length = 120)
    private String lastname;

    private Integer age;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 120)
    private String birthPlace;

    @Column(name = "birth_country", length = 100)
    private String birthCountry;

    @Column(length = 100)
    private String nationality;

    /** API "190 cm" / "84 kg" string formatinda. */
    @Column(length = 20)
    private String height;

    @Column(length = 20)
    private String weight;

    private Boolean injured;

    /**
     * Kapsam bayragi — DailyPlayerRefreshJob bu oyuncunun profile/career/
     * trophies/stats verilerini gunluk tazeler. Tipik olarak covered takimin
     * kadrosundaki oyuncular auto-mark edilir.
     */
    @Column(nullable = false)
    private boolean covered;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * UI/search'te gosterilecek tam isim. API-Football lineup/stats/topplayer
     * endpoint'leri kisa form ("A. Guler") doner; master profile sync
     * sonrasi firstname+lastname dolar. Bu helper varsa tam ad ("Arda Guler")
     * doner, yoksa kisa name fallback.
     *
     * <p>Lineup, FixturePlayerStat, LeagueTopPlayer, PlayerDoc surface'leri
     * Player FK'sini cozdugunde bu metodu cagirmali.
     */
    public String getDisplayName() {
        String f = firstname == null ? null : firstname.trim();
        String l = lastname == null ? null : lastname.trim();
        boolean hasFirst = f != null && !f.isEmpty();
        boolean hasLast = l != null && !l.isEmpty();
        if (hasFirst && hasLast) {
            return f + " " + l;
        }
        if (hasLast) return l;
        if (hasFirst) return f;
        return name;
    }
}

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
 * Tekik direktor (coach) master kayit. API-Football'un coach id'si PK.
 *
 * <p>Foto aynalamasi: {@code photo_url} → {@code photo_key} (MinIO).
 * Aynalama {@link com.scorestv.football.image.ImageMirrorService} icinde
 * {@code mirrorCoachPhotos} tarafindan yapilir.
 */
@Entity
@Table(name = "coaches")
@Getter
@Setter
@NoArgsConstructor
public class Coach {

    @Id
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

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

    /** API "192 cm" gibi string. */
    @Column(length = 20)
    private String height;

    @Column(length = 20)
    private String weight;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    /** Su anki takim (varsa). Tarihsel kayitlar coach_career'da. */
    @Column(name = "current_team_id")
    private Long currentTeamId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

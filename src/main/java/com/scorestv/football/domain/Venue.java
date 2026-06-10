package com.scorestv.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Stadyum. API-Football'un venue ID'si birincil anahtar olarak kullanılır.
 */
@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
public class Venue implements TranslatableName {

    /** API-Football venue id (atanmış; üretilmez). */
    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Elle girilen Türkçe stadyum adı; henüz çevrilmemişse null. Senkron
     * upsert'i bu alana DOKUNMAZ.
     */
    @Column(name = "name_tr", length = 150)
    private String nameTr;

    @Column(length = 255)
    private String address;

    @Column(length = 120)
    private String city;

    private Integer capacity;

    @Column(length = 50)
    private String surface;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    /** MinIO'da aynalanan stadyum gorseli; aynalanana dek null. */
    @Column(name = "image_key", length = 255)
    private String imageKey;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

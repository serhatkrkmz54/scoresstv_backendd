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
 * Lig veya kupa. API-Football'un league ID'si birincil anahtar olarak kullanılır.
 *
 * <p>{@code covered} bayrağı, bu ligin fikstür/puan durumu senkronuna dahil
 * olup olmadığını belirler. Tüm ligler API'den çekilip saklanır; ADMIN
 * hangilerinin kapsama alınacağını bu bayrakla seçer.
 */
@Entity
@Table(name = "leagues")
@Getter
@Setter
@NoArgsConstructor
public class League implements TranslatableName {

    /** API-Football league id (atanmış; üretilmez). */
    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Elle girilen Türkçe lig adı; henüz çevrilmemişse null. Senkron upsert'i
     * bu alana DOKUNMAZ.
     */
    @Column(name = "name_tr", length = 150)
    private String nameTr;

    /** 'League' veya 'Cup'. */
    @Column(length = 20)
    private String type;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    /** MinIO'da aynalanan logonun nesne anahtarı; aynalanana dek null. */
    @Column(name = "logo_key", length = 255)
    private String logoKey;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "country_flag_url", length = 255)
    private String countryFlagUrl;

    /** Geçerli sezon yılı (örn. 2025). */
    @Column(name = "current_season")
    private Integer currentSeason;

    /** Bu lig fikstür/puan durumu senkronuna dahil mi? */
    @Column(nullable = false)
    private boolean covered;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

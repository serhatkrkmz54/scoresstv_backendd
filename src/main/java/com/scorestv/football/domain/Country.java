package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ülke — API-Football /countries referans verisi.
 *
 * <p>API-Football ülke kodu bazı kayıtlarda (örn. "World") null olabildiği için
 * birincil anahtar üretilen id; ad ise benzersizdir ve upsert anahtarıdır.
 */
@Entity
@Table(
        name = "countries",
        uniqueConstraints = @UniqueConstraint(name = "uq_countries_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
public class Country extends BaseEntity implements TranslatableName {

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Elle girilen Türkçe ülke adı (örn. "Germany" → "Almanya"); henüz
     * çevrilmemişse null. Senkron upsert'i bu alana DOKUNMAZ.
     */
    @Column(name = "name_tr", length = 100)
    private String nameTr;

    /** ISO ülke kodu (örn. "TR", "GB"); uluslararası kayıtlarda null olabilir. */
    @Column(length = 10)
    private String code;

    @Column(name = "flag_url", length = 255)
    private String flagUrl;

    /** MinIO'da aynalanan bayrağın nesne anahtarı; aynalanana dek null. */
    @Column(name = "flag_key", length = 255)
    private String flagKey;
}

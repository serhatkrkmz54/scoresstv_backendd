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

/** Basketbol ülkesi (referans veri). Football'dan ayrı tablo. */
@Entity
@Table(name = "basketball_countries")
@Getter
@Setter
@NoArgsConstructor
public class BasketballCountry {

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Türkçe ülke adı (manuel). Sync dokunmaz. */
    @Column(length = 120)
    private String nameTr;

    @Column(length = 10)
    private String code;

    @Column(length = 400)
    private String flag;

    @UpdateTimestamp
    private Instant updatedAt;
}

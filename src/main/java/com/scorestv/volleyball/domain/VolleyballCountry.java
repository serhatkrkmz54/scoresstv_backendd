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

/** Voleybol ulkesi (referans veri). Football/basketball'dan ayri tablo. */
@Entity
@Table(name = "volleyball_countries")
@Getter
@Setter
@NoArgsConstructor
public class VolleyballCountry {

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Turkce ulke adi (manuel). Sync dokunmaz. */
    @Column(length = 120)
    private String nameTr;

    @Column(length = 10)
    private String code;

    @Column(length = 400)
    private String flag;

    @UpdateTimestamp
    private Instant updatedAt;
}

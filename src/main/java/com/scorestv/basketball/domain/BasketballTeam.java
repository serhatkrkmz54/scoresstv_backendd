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

/** Basketbol takımı (API-Basketball team id = PK). Football'dan ayrı tablo. */
@Entity
@Table(name = "basketball_teams")
@Getter
@Setter
@NoArgsConstructor
public class BasketballTeam {

    @Id
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** Türkçe ad (manuel/editör girişi). Sync ASLA dokunmaz; TR locale'de
     *  gösterilir, boşsa name'e düşülür. */
    @Column(length = 160)
    private String nameTr;

    /** API logo URL'i (aynalanacak kaynak). */
    @Column(length = 400)
    private String logo;

    /** MinIO/CDN nesne anahtarı — aynalandıysa dolar; serving CDN URL'i bundan kurar. */
    @Column(length = 255)
    private String logoKey;

    @UpdateTimestamp
    private Instant updatedAt;
}

package com.scorestv.broadcasts.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.League;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lig × Sezon × Ulke icin varsayilan TV kanali.
 *
 * <p>"Super Lig 2025 sezonu TR'de beIN SPORTS 1 ve beIN SPORTS 4'te yayinlanir"
 * gibi sezonluk bilgi. Mac bazinda override yoksa bu liste kullanilir.
 *
 * <p>Bir lig icin birden cok kanal kayit olabilir (ana + alternatif).
 * {@code sort_order} ile listelenir.
 */
@Entity
@Table(
        name = "league_broadcasters",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_league_broadcasters",
                columnNames = {"league_id", "season", "country_code", "channel_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class LeagueBroadcaster extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private Integer season;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private TvChannel channel;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(columnDefinition = "TEXT")
    private String notes;
}

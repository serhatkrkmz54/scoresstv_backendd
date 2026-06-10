package com.scorestv.broadcasts.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.Fixture;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tek bir mac icin TV yayin atamasi — override.
 *
 * <p>Bu kayit varsa frontend bunu gosterir, lig default'unu atlar.
 * Tipik kullanim: derbi ozel kanal, milli mac TRT, dostluk maci farkli vs.
 *
 * <p>{@code source} kaydin kaynagi: admin manuel mi, livesoccertv scrape mi,
 * excel import mu — UI rozetinde gosterilebilir.
 */
@Entity
@Table(
        name = "match_broadcasts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_match_broadcasts",
                columnNames = {"fixture_id", "country_code", "channel_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class MatchBroadcast extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private TvChannel channel;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BroadcastSource source = BroadcastSource.MANUAL;

    /** Yayin kaydinin kaynagi — UI rozeti icin. */
    public enum BroadcastSource {
        MANUAL,
        LIVESOCCERTV,
        IMPORT
    }
}

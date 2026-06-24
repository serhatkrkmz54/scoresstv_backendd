package com.scorestv.volleyball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Lig + sezon coverage cache — standings DailyJob icin.
 *
 * <p>Basketbol "seasons" tablosunun voleybol esi. {@code coverageStandings}
 * yalniz true ise standings cekilir. {@code standingsLastSyncedAt} son
 * tazeleme zamani.
 */
@Entity
@Table(
    name = "volleyball_seasons",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_vb_seasons",
        columnNames = {"league_id", "season"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class VolleyballSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private VolleyballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    @Column(nullable = false)
    private boolean coverageStandings = true;

    private Instant standingsLastSyncedAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

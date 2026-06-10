package com.scorestv.football.domain;

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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bir maçta bir takıma ait tek bir istatistik satırı (örn. "Shots on Goal: 3").
 *
 * <p>{@code value} VARCHAR — Integer ("3"), String ("32%") veya null. Sync
 * tickleriyle yeni tipler de geldikçe şema değişikliği gerekmez; frontend
 * ihtiyaca göre parse eder.
 *
 * <p>Sync replace pattern kullanır: aynı maçın tüm satırları silinip yeniden
 * yazılır → eski tip API'dan kaybolursa otomatik düşer.
 */
@Entity
@Table(
        name = "fixture_statistics",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fixture_statistics",
                columnNames = {"fixture_id", "team_id", "stat_type"})
)
@Getter
@Setter
@NoArgsConstructor
public class FixtureStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** Stat etiketi — API'dan gelir, "Shots on Goal", "Ball Possession", vb. */
    @Column(name = "stat_type", nullable = false, length = 40)
    private String statType;

    /** Değer — Integer/String/null olabildiği için VARCHAR. */
    @Column(name = "stat_value", length = 30)
    private String statValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

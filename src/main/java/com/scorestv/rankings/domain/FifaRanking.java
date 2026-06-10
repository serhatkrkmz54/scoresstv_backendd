package com.scorestv.rankings.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIFA Erkek Milli Takim Siralamasi tek bir satiri.
 *
 * <p>Kaynak: {@code https://api.fifa.com/api/v3/fifarankings/rankings/live}
 * Gunluk tazelenir; her sync tablo bosaltilip yeniden yazilir.
 *
 * <p>{@code team_id} FIFA'nin kullandigi id (string — orn. "43946" Fransa).
 * {@code country_code} 3-harfli ISO kodu (orn. "FRA").
 * {@code confederation} UEFA / CONMEBOL / CAF / AFC / CONCACAF / OFC.
 */
@Entity
@Table(
        name = "fifa_rankings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fifa_rankings_team_id",
                columnNames = {"team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class FifaRanking extends BaseEntity {

    @Column(nullable = false)
    private Integer rank;

    /** Onceki ranking guncellemesindeki sirasi. */
    @Column(name = "prev_rank")
    private Integer prevRank;

    /** Pozitif: ust siralara cikti; negatif: dustu; 0: stabil. */
    private Integer movement;

    /** FIFA'nin team id'si (string — bazi degerler 7 haneli olabilir). */
    @Column(name = "team_id", nullable = false, length = 50)
    private String teamId;

    @Column(name = "team_name", nullable = false, length = 120)
    private String teamName;

    /** 3-harfli ISO kodu — orn. "FRA", "ESP", "TUR". */
    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    /** "UEFA", "CONMEBOL", "CAF", "AFC", "CONCACAF", "OFC". */
    @Column(length = 20)
    private String confederation;

    /** FIFA'nin konfederasyon id'si (orn. "27275" UEFA). */
    @Column(name = "confederation_id", length = 50)
    private String confederationId;

    /** FIFA'nin puani — kesirli (orn. 1877.322731). */
    @Column(name = "total_points", nullable = false, precision = 10, scale = 6)
    private BigDecimal totalPoints;

    @Column(name = "prev_points", precision = 10, scale = 6)
    private BigDecimal prevPoints;

    /** Hesaplamada kullanilan toplam mac sayisi. */
    @Column(name = "rated_matches")
    private Integer ratedMatches;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}

package com.scorestv.rankings.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * UEFA Kulup Katsayisi tek bir kulup satiri.
 *
 * <p>Kaynak: {@code https://comp.uefa.com/v2/coefficients?coefficientType=MEN_CLUB}
 * Sezon basina ~415 kulup; 5 sezon toplami (sporting) ile siralanir.
 *
 * <p>{@code season_rankings_json} her satirda son 5 sezonun ayri puanlarini
 * tutar — frontend "son 5 sezon detayi" widget'i icin gerekirse.
 */
@Entity
@Table(
        name = "uefa_club_rankings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_uefa_club_rankings_club_season",
                columnNames = {"club_id", "target_season_year"})
)
@Getter
@Setter
@NoArgsConstructor
public class UefaClubRanking extends BaseEntity {

    /** UEFA'nin club id'si (string — orn. "50037" Bayern). */
    @Column(name = "club_id", nullable = false, length = 50)
    private String clubId;

    @Column(name = "club_name", nullable = false, length = 120)
    private String clubName;

    @Column(name = "club_short_name", length = 80)
    private String clubShortName;

    @Column(name = "club_official_name", length = 160)
    private String clubOfficialName;

    @Column(name = "team_code", length = 10)
    private String teamCode;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "big_logo_url", columnDefinition = "TEXT")
    private String bigLogoUrl;

    @Column(name = "medium_logo_url", columnDefinition = "TEXT")
    private String mediumLogoUrl;

    /** 3-harfli ISO ulke kodu — orn. "GER", "ESP", "TUR". */
    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "country_name", length = 80)
    private String countryName;

    /** UEFA'nin federasyon id'si. */
    @Column(name = "association_id")
    private Long associationId;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "total_points", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalPoints;

    /** "UP" / "DOWN" / "STABLE" — onceki guncellemeye gore. */
    @Column(length = 10)
    private String trend;

    @Column(name = "number_of_matches")
    private Integer numberOfMatches;

    @Column(name = "number_of_teams")
    private Integer numberOfTeams;

    /** Hedef sezon — orn. 2026 sezonu icin hesaplanan ranking. */
    @Column(name = "target_season_year", nullable = false)
    private Integer targetSeasonYear;

    /** Hesaplamanin basladigi en eski sezon (5 sezon onceki). */
    @Column(name = "base_season_year")
    private Integer baseSeasonYear;

    /**
     * Son 5 sezonun ayri puanlari — JSONB. Frontend istedigi alani okur:
     * seasonYear, totalPoints, position, numberOfMatches, factorTotalBonus vb.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "season_rankings_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> seasonRankingsJson;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}

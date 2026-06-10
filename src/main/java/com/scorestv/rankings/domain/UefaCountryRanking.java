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
 * UEFA Milli Takim Katsayisi tek bir ulke satiri.
 *
 * <p>Kaynak: {@code https://comp.uefa.com/v2/coefficients?coefficientType=MEN_ASSOCIATION}
 * 55 UEFA uyesi federasyon; 5 sezon toplami ile siralanir.
 *
 * <p>FIFA milli takim siralamasi farklidir (rating sistemi). Bu UEFA'nin kendi
 * katsayisi — Euro elemelerinde torba siralamasi icin kullanilir.
 */
@Entity
@Table(
        name = "uefa_country_rankings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_uefa_country_rankings_country_season",
                columnNames = {"country_uefa_id", "target_season_year"})
)
@Getter
@Setter
@NoArgsConstructor
public class UefaCountryRanking extends BaseEntity {

    /** UEFA'nin country id'si (orn. "39" Ingiltere). */
    @Column(name = "country_uefa_id", nullable = false, length = 50)
    private String countryUefaId;

    @Column(name = "country_name", nullable = false, length = 80)
    private String countryName;

    /** 3-harfli ISO ulke kodu — orn. "ENG", "ITA", "TUR". */
    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "big_logo_url", columnDefinition = "TEXT")
    private String bigLogoUrl;

    @Column(name = "medium_logo_url", columnDefinition = "TEXT")
    private String mediumLogoUrl;

    @Column(name = "association_id")
    private Long associationId;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "total_points", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalPoints;

    @Column(length = 10)
    private String trend;

    @Column(name = "number_of_matches")
    private Integer numberOfMatches;

    @Column(name = "number_of_teams")
    private Integer numberOfTeams;

    @Column(name = "target_season_year", nullable = false)
    private Integer targetSeasonYear;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "season_rankings_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> seasonRankingsJson;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
}

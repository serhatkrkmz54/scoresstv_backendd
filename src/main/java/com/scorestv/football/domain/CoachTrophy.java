package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
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
 * Bir kocun kazandigi tek bir kupa. API:
 *   {@code GET /trophies?coach=X}
 *
 * <p>Takim sayfasinda mevcut kocun trophy listesi gosterilir.
 */
@Entity
@Table(
        name = "coach_trophies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coach_trophies_unique",
                columnNames = {"coach_id", "league", "season", "place"})
)
@Getter
@Setter
@NoArgsConstructor
public class CoachTrophy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    @Column(nullable = false, length = 255)
    private String league;

    @Column(length = 100)
    private String country;

    /** API "2018/2019" gibi string — biz ham string sakliyoruz. */
    @Column(length = 50)
    private String season;

    /** "Winner" / "2nd Place" / vb. */
    @Column(length = 50)
    private String place;
}

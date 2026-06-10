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

import java.time.LocalDate;

/**
 * Bir kocun kariyer dönemi (gecmis veya mevcut). API:
 *   {@code GET /coachs?id=X} yaniti icindeki {@code career[]} dizisi.
 *
 * <p>{@code end_date NULL} → halen devam ediyor (mevcut takim).
 */
@Entity
@Table(
        name = "coach_career",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coach_career_unique",
                columnNames = {"coach_id", "team_id", "start_date"})
)
@Getter
@Setter
@NoArgsConstructor
public class CoachCareer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private Coach coach;

    /** Takim master ile referans; null olabilir (API'de takim id eksikse). */
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "team_name", length = 150)
    private String teamName;

    @Column(name = "team_logo", length = 500)
    private String teamLogo;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}

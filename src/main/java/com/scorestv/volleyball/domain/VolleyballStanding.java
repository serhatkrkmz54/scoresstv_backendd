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
 * Bir takimin bir lig+sezon'daki puan durumu (voleybol).
 *
 * <p>Voleybolda beraberlik (draw) YOK — sadece galibiyet/maglubiyet.
 * {@code setsFor}/{@code setsAgainst} = toplam kazanilan/kaybedilen set.
 * Grup ayrimi olmayan liglerde {@code groupName} bos string ('') — unique
 * constraint NULL'a izin vermez.
 */
@Entity
@Table(
    name = "volleyball_standings",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_vb_standings",
        columnNames = {"league_id", "season", "team_id", "group_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class VolleyballStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private VolleyballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private VolleyballTeam team;

    @Column(length = 80)
    private String stage;

    @Column(name = "group_name", nullable = false, length = 80)
    private String groupName = "";

    private Integer position;

    // ---- Oynanan / galibiyet / maglubiyet ----
    private Integer gamesPlayed;
    private Integer won;
    private Integer lost;
    @Column(length = 10)
    private String wonPercentage;
    @Column(length = 10)
    private String lostPercentage;

    // ---- Set farki (goals.for / goals.against) ----
    private Integer setsFor;
    private Integer setsAgainst;

    /** Puan (voleybolda standings'in ayri "points" alani var). */
    private Integer points;

    @Column(length = 20)
    private String form;

    @Column(length = 200)
    private String description;

    @UpdateTimestamp
    private Instant updatedAt;
}

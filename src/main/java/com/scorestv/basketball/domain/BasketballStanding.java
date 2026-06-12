package com.scorestv.basketball.domain;

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
 * Bir takimin bir lig+sezon'daki puan durumu (basketbol).
 *
 * <p>NBA gibi liglerde {@code groupName} "Eastern Conference" / "Western
 * Conference" olur; EuroLeague Final Four icin "Group A" gibi. Grup
 * ayrimi olmayan liglerde bos string ('') kullanilir — unique constraint
 * NULL'a izin vermez.
 */
@Entity
@Table(
    name = "basketball_standings",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bk_standings",
        columnNames = {"league_id", "season", "team_id", "group_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private BasketballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private BasketballTeam team;

    @Column(length = 80)
    private String stage;

    @Column(name = "group_name", nullable = false, length = 80)
    private String groupName = "";

    private Integer position;

    // ---- Galibiyetler ----
    private Integer wonAll;
    private Integer wonHome;
    private Integer wonAway;
    @Column(length = 10)
    private String wonPercentage;

    // ---- Maglubiyetler ----
    private Integer lostAll;
    private Integer lostHome;
    private Integer lostAway;
    @Column(length = 10)
    private String lostPercentage;

    // ---- Oynanan mac sayilari ----
    private Integer gamesPlayedAll;
    private Integer gamesPlayedHome;
    private Integer gamesPlayedAway;

    // ---- Sayi farki ----
    private Integer pointsFor;
    private Integer pointsAgainst;

    @Column(length = 20)
    private String form;

    @Column(length = 200)
    private String description;

    @UpdateTimestamp
    private Instant updatedAt;
}

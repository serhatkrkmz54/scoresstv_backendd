package com.scorestv.basketball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bir lig + sezon icin 3 kategori (SCORERS / REBOUNDERS / ASSISTS) basina
 * top 10 oyuncu sirasi. NBA / EuroLeague gibi liglerin lig sayfasinda
 * "En Skorerlar / Ribaund / Asist" listeleri.
 *
 * <p>{@code BasketballPlayerSeasonStat} tablosundan beslenir: sezon icin
 * tum oyuncular PPG/RPG/APG'ye gore siralanir, top 10 alinir, bu tabloya
 * yazilir. Replace stratejisi — (league, season, category) icin once
 * silinir, sonra insert.
 *
 * <p>Futboldaki {@code LeagueTopPlayer} patterninin basketbol esi.
 *
 * <p>Unique key: (league, season, category, position) — ayni siralamada
 * cakisma yok.
 */
@Entity
@Table(
        name = "basketball_league_top_players",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bk_top_players",
                columnNames = {"league_id", "season", "category", "position"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BasketballLeagueTopPlayer {

    public enum Category {
        SCORERS,
        REBOUNDERS,
        ASSISTS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private BasketballLeague league;

    @Column(nullable = false, length = 20)
    private String season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    /** Siralamadaki konum (1-10). */
    @Column(nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private BasketballPlayer player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private BasketballTeam team;

    /** Siralama metrigi (PPG / RPG / APG — kategoriye gore). */
    @Column(precision = 6, scale = 2)
    private BigDecimal value;

    /** Referans — kac mac oynamis (cok az mac = istatistik anlamsiz). */
    @Column
    private Integer gamesPlayed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}

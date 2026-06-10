package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bir lig+sezon icin tek bir top-N oyuncu satiri.
 *
 * <p>{@link Category} discriminator'i ile uc kategori ayni tabloda tutulur
 * (scorers, assists, cards). Sik sorgu: lig+sezon+kategori filtresiyle rank
 * sirali ilk N satir — bu yuzden index (league_id, season, category, rank).
 *
 * <p>API endpoint'leri ayni yapida player+statistics doner; sadece
 * kategorinin "value" alani degisir:
 * <ul>
 *   <li>SCORERS → {@code goals.total}</li>
 *   <li>ASSISTS → {@code goals.assists}</li>
 *   <li>CARDS   → {@code cards.yellow} (kirmizi {@code valueSecondary})</li>
 * </ul>
 */
@Entity
@Table(
        name = "league_top_players",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_top_players_unique",
                columnNames = {"league_id", "season", "category", "player_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class LeagueTopPlayer extends BaseEntity {

    /**
     * Top oyuncu kategorisi — API'nin ayri endpoint'lerine 1-1 esler:
     * <ul>
     *   <li>SCORERS      → {@code /players/topscorers}</li>
     *   <li>ASSISTS      → {@code /players/topassists}</li>
     *   <li>YELLOW_CARDS → {@code /players/topyellowcards}</li>
     *   <li>RED_CARDS    → {@code /players/topredcards}</li>
     * </ul>
     * Eski "CARDS" tek-kategori yaklasimi kaldirildi; API zaten sari ve
     * kirmiziyi ayri endpoint'te tutar.
     */
    public enum Category {
        SCORERS, ASSISTS, YELLOW_CARDS, RED_CARDS
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private Integer season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "player_name", nullable = false, length = 255)
    private String playerName;

    @Column(name = "player_photo", length = 500)
    private String playerPhoto;

    @Column(name = "player_nationality", length = 100)
    private String playerNationality;

    @Column(name = "player_age")
    private Integer playerAge;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "team_name", length = 150)
    private String teamName;

    @Column(name = "team_logo", length = 500)
    private String teamLogo;

    @Column(name = "value_primary")
    private Integer valuePrimary;

    @Column(name = "value_secondary")
    private Integer valueSecondary;

    @Column(name = "appearances")
    private Integer appearances;

    @Column(name = "minutes")
    private Integer minutes;
}

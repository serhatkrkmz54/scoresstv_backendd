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
 * Bir ligin belirli bir sezonundaki tek takımın puan durumu satırı.
 *
 * <p>(lig, sezon, takım, grup_adı) dörtlüsü benzersizdir; senkronda upsert
 * bu dörtlüye göre yapılır.
 *
 * <p>Grup adı dahil çünkü bazı kupalarda (TR Kupası gibi) aynı takım birden
 * çok grupta yer alabilir: gerçek grubu (Group B) + 3.lik ranking grubu
 * (Group D). Önceki UNIQUE(league, season, team) constraint bunu engelliyor,
 * sync transaction'ı silent fail ediyordu — V34 migration ile düzeltildi.
 */
@Entity
@Table(
        name = "standings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_standings_league_season_team_group",
                columnNames = {"league_id", "season", "team_id", "group_name"})
)
@Getter
@Setter
@NoArgsConstructor
public class Standing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @Column(nullable = false)
    private Integer season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** Sıralamadaki yer (1 = lider). */
    @Column(nullable = false)
    private Integer rank;

    /** Grup adı (gruplu turnuvalarda); lig için genelde tek grup. */
    @Column(name = "group_name", length = 80)
    private String groupName;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "goals_diff")
    private Integer goalsDiff;

    /** Son maçların formu, örn. "WWDLW". */
    @Column(length = 100)
    private String form;

    /** Açıklama, örn. "Promotion - Champions League (Group Stage)". */
    @Column(length = 150)
    private String description;

    private Integer played;

    private Integer win;

    private Integer draw;

    private Integer lose;

    @Column(name = "goals_for")
    private Integer goalsFor;

    @Column(name = "goals_against")
    private Integer goalsAgainst;
}

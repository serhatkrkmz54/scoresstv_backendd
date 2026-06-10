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

import java.time.Instant;

/**
 * Bir maçtaki tek bir takımın kadrosu (formasyon + koç + renkler).
 * Her maç için iki satır olur (ev + deplasman).
 *
 * <p>Oyuncular ayrı tabloda ({@link FixtureLineupPlayer}); senkronda
 * oyuncular silinip yeniden yazılır (replace), bu satır UPDATE edilir.
 *
 * <p>{@code announced_at} alanı YALNIZ ilk insert'te ayarlanır
 * ({@code updatable = false}); sonraki sync'ler değişikliği koruz, dolayısıyla
 * "kadro 2 saat önce açıklandı" göstergesi geçerli kalır.
 */
@Entity
@Table(
        name = "fixture_lineups",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fixture_lineups_fixture_team",
                columnNames = {"fixture_id", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class FixtureLineup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(length = 20)
    private String formation;

    @Column(name = "coach_id")
    private Long coachId;

    @Column(name = "coach_name", length = 120)
    private String coachName;

    @Column(name = "coach_photo", length = 255)
    private String coachPhoto;

    @Column(name = "player_color_primary", length = 8)
    private String playerColorPrimary;

    @Column(name = "player_color_number", length = 8)
    private String playerColorNumber;

    @Column(name = "player_color_border", length = 8)
    private String playerColorBorder;

    @Column(name = "gk_color_primary", length = 8)
    private String gkColorPrimary;

    @Column(name = "gk_color_number", length = 8)
    private String gkColorNumber;

    @Column(name = "gk_color_border", length = 8)
    private String gkColorBorder;

    /**
     * Kadronun ilk kez API'ye düştüğü an. Upserter ilk insert'te set eder;
     * {@code updatable = false} ile sonraki UPDATE'lerden korunur.
     */
    @Column(name = "announced_at", nullable = false, updatable = false)
    private Instant announcedAt;
}

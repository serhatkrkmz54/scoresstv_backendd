package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Tek bir transfer hareketi (flat). API:
 *   {@code GET /transfers?team=X}
 *
 * <p>Yanit ic ice geliyor (her oyuncunun TUM kariyer transferleri); biz
 * takima ait satirlari cikarip flat olarak buraya yaziyoruz.
 * "Bu takima gelen/giden" sorgusu indeksli olarak {@code in_team_id} /
 * {@code out_team_id} uzerinden yapilir.
 */
@Entity
@Table(
        name = "transfers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_transfers_player_date_in_out",
                columnNames = {"player_id", "transfer_date", "in_team_id", "out_team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class Transfer extends BaseEntity {

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "player_name", nullable = false, length = 255)
    private String playerName;

    @Column(name = "transfer_date")
    private LocalDate transferDate;

    /** "Free" / "Loan" / "$X.XM" gibi API metni. */
    @Column(name = "transfer_type", length = 50)
    private String transferType;

    @Column(name = "in_team_id")
    private Long inTeamId;

    @Column(name = "in_team_name", length = 150)
    private String inTeamName;

    @Column(name = "in_team_logo", length = 500)
    private String inTeamLogo;

    @Column(name = "out_team_id")
    private Long outTeamId;

    @Column(name = "out_team_name", length = 150)
    private String outTeamName;

    @Column(name = "out_team_logo", length = 500)
    private String outTeamLogo;
}

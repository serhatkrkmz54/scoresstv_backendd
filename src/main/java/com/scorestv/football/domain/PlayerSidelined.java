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
 * Oyuncu sakatlik / cezalik gecmis kaydi. API:
 *   {@code GET /sidelined?player=X}  (tek oyuncu)
 *   {@code GET /sidelined?players=A-B-C}  (batch)
 *
 * <p>Takim sayfasinda kadronun AKTIF (end_date NULL veya >= bugun)
 * kayitlari gosterilir.
 */
@Entity
@Table(
        name = "player_sidelined",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_player_sidelined_unique",
                columnNames = {"player_id", "type", "start_date"})
)
@Getter
@Setter
@NoArgsConstructor
public class PlayerSidelined extends BaseEntity {

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** "Hip/Thigh Injury" / "Suspended" / "Broken Toe" gibi. */
    @Column(nullable = false, length = 120)
    private String type;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}

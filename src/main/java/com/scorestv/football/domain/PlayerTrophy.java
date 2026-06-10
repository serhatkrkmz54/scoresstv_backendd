package com.scorestv.football.domain;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bir oyuncunun kazandigi tek bir kupa. API:
 *   {@code GET /trophies?player=X}
 *
 * <p><b>Not:</b> API'nin coach ve player trophy endpoint'leri ayni person id'de
 * (ornek: Sahin) AYNI sonucu doner — biz yine de player_trophies + coach_trophies
 * ayri tablolarda tutuyoruz (cleaner pattern). Player sayfasinda hepsi
 * "kariyer kupalari" olarak gosterilir.
 *
 * <p>UNIQUE (player_id, league, season, place) — ayni kupanin tekrar yazilmasini
 * engeller (rare ama API'den duplicate gelirse).
 */
@Entity
@Table(
        name = "player_trophies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_player_trophies",
                columnNames = {"player_id", "league", "season", "place"})
)
@Getter
@Setter
@NoArgsConstructor
public class PlayerTrophy extends BaseEntity {

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 255)
    private String league;

    @Column(length = 100)
    private String country;

    @Column(length = 50)
    private String season;

    /** "Winner" / "2nd Place" / vb. */
    @Column(length = 50)
    private String place;
}

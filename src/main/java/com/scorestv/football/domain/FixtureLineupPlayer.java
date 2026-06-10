package com.scorestv.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bir kadrodaki tek bir oyuncu (ilk 11 ya da yedek).
 *
 * <p>API-Football player id'si saklanır (henüz oyuncu tablosu yok, FK değil).
 * Senkron her tick'te oyuncuları siler ve gelenleri yeniden yazar — bu
 * yüzden {@code updated_at} alanı tutulmaz, yalnız {@code created_at}.
 */
@Entity
@Table(name = "fixture_lineup_players")
@Getter
@Setter
@NoArgsConstructor
public class FixtureLineupPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lineup_id", nullable = false)
    private FixtureLineup lineup;

    /** API-Football player id (FK değil, oyuncu tablosu henüz yok). */
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "player_name", length = 120)
    private String playerName;

    @Column(name = "jersey_number")
    private Integer jerseyNumber;

    /** Pozisyon kodu: G (kaleci), D (defans), M (orta), F (forvet). */
    @Column(length = 5)
    private String position;

    /** Saha üzerindeki konum "X:Y" — X satır, Y sütun. Yedeklerde null. */
    @Column(length = 10)
    private String grid;

    /** false = ilk 11, true = yedek. */
    @Column(name = "is_substitute", nullable = false)
    private boolean substitute;

    /** API'den gelen sırayı korumak için. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

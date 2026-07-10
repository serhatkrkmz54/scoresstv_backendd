package com.scorestv.bilyoner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maç öncesi Bilyoner oranlarının kalıcı snapshot'ı — value/backtest analizi
 * için. Her fixture için 'opening' (~24s kala) ve 'closing' (~90dk kala) birer
 * satır. YALNIZCA İÇERİDE kullanılır; hiçbir public uçtan servis edilmez.
 */
@Entity
@Table(
        name = "fixture_odds_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fixture_odds_snapshot",
                columnNames = {"fixture_id", "source", "snapshot_kind"}))
@Getter
@Setter
@NoArgsConstructor
public class FixtureOddsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    /** Kaynak — şimdilik yalnız "bilyoner". */
    @Column(nullable = false, length = 20)
    private String source;

    /** "opening" | "closing". */
    @Column(name = "snapshot_kind", nullable = false, length = 10)
    private String snapshotKind;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    /** Snapshot anında kickoff'a kalan dakika (çizgi hareketi analizi için). */
    @Column(name = "minutes_to_kickoff")
    private Integer minutesToKickoff;

    // Maç Sonucu (1X2)
    @Column(name = "odd_home") private Double oddHome;
    @Column(name = "odd_draw") private Double oddDraw;
    @Column(name = "odd_away") private Double oddAway;

    // 2.5 Alt/Üst
    @Column(name = "odd_over25") private Double oddOver25;
    @Column(name = "odd_under25") private Double oddUnder25;

    // Karşılıklı Gol
    @Column(name = "odd_btts_yes") private Double oddBttsYes;
    @Column(name = "odd_btts_no") private Double oddBttsNo;
}

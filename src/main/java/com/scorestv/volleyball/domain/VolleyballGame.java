package com.scorestv.volleyball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Voleybol maci (API-Volleyball game id = PK).
 *
 * <p><b>Skor modeli (basketboldan FARKLI):</b>
 * <ul>
 *   <li>{@code homeTotal}/{@code awayTotal} = KAZANILAN SET sayisi (0..3).</li>
 *   <li>{@code home/awaySet1..5} = her setteki SAYI (nullable, orn 25-21).</li>
 * </ul>
 * Voleybolda ceyrek/overtime YOK; timer/clock YOK (set bazli).
 */
@Entity
@Table(name = "volleyball_games")
@Getter
@Setter
@NoArgsConstructor
public class VolleyballGame {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private VolleyballLeague league;

    @Column(length = 20)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private VolleyballTeam homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private VolleyballTeam awayTeam;

    @Column(nullable = false)
    private Instant startAt;

    /** Durum kisa kodu: NS, S1..S5, AW, POST, CANC, INTR, ABD, FT. */
    @Column(length = 10)
    private String statusShort;

    @Column(length = 60)
    private String statusLong;

    @Column(length = 60)
    private String stage;

    @Column(length = 60)
    private String week;

    // ---- Skorlar: kazanilan set sayisi (0..3) ----
    private Integer homeTotal;
    private Integer awayTotal;

    // ---- Set bazli sayilar (her set icin home/away, nullable) ----
    private Integer homeSet1;
    private Integer homeSet2;
    private Integer homeSet3;
    private Integer homeSet4;
    private Integer homeSet5;

    private Integer awaySet1;
    private Integer awaySet2;
    private Integer awaySet3;
    private Integer awaySet4;
    private Integer awaySet5;

    // ---- Bildirim durum izleme (FCM push idempotency) ----
    /** Mac basladi push'u gonderildi mi (NS->canli gecisinde bir kez). */
    @Column(nullable = false)
    private boolean notifiedStart = false;

    /** Mac bitti push'u gonderildi mi (->FT/AW gecisinde bir kez). */
    @Column(nullable = false)
    private boolean notifiedFinal = false;

    /** Sonu bildirilen en yuksek set (0=hic, 1..5). Monotonik artar. */
    @Column(nullable = false)
    private int lastNotifiedPeriod = 0;

    @UpdateTimestamp
    private Instant lastSyncedAt;
}

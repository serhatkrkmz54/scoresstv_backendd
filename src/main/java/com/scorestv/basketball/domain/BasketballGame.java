package com.scorestv.basketball.domain;

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
 * Basketbol maçı (API-Basketball game id = PK).
 *
 * <p>Football'dan farkı: skorlar çeyrek bazlı ({@code home/awayQ1..Q4} +
 * {@code overTime} + {@code total}). Anasayfa fikstür + canlı skor aynı
 * satırdan beslenir: {@code statusShort} durumu, {@code timer} canlı saati,
 * {@code home/awayTotal} anlık skoru tutar.
 */
@Entity
@Table(name = "basketball_games")
@Getter
@Setter
@NoArgsConstructor
public class BasketballGame {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private BasketballLeague league;

    @Column(length = 20)
    private String season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private BasketballTeam homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private BasketballTeam awayTeam;

    @Column(nullable = false)
    private Instant startAt;

    /** Durum kısa kodu: NS, Q1, Q2, HT, Q3, Q4, OT, BT, FT, AOT, POST, CANC... */
    @Column(length = 10)
    private String statusShort;

    @Column(length = 60)
    private String statusLong;

    /** Oyun saati ("5:23") veya kalan dakika — canlı maçta dolu. */
    @Column(length = 20)
    private String timer;

    @Column(length = 60)
    private String stage;

    @Column(length = 60)
    private String week;

    // ---- Skorlar (çeyrek bazlı) ----
    private Integer homeQ1;
    private Integer homeQ2;
    private Integer homeQ3;
    private Integer homeQ4;
    private Integer homeOt;
    private Integer homeTotal;

    private Integer awayQ1;
    private Integer awayQ2;
    private Integer awayQ3;
    private Integer awayQ4;
    private Integer awayOt;
    private Integer awayTotal;

    // ---- Bildirim durum izleme (FCM push idempotency) ----
    /** Maç başladı push'u gönderildi mi (NS→canlı geçişinde bir kez). */
    @Column(nullable = false)
    private boolean notifiedStart = false;

    /** Maç bitti push'u gönderildi mi (→FT/AOT geçişinde bir kez). */
    @Column(nullable = false)
    private boolean notifiedFinal = false;

    /** Sonu bildirilen en yüksek çeyrek (0=hiç, 1..4). Monotonik artar. */
    @Column(nullable = false)
    private int lastNotifiedPeriod = 0;

    @UpdateTimestamp
    private Instant lastSyncedAt;
}

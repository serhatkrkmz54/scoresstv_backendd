package com.scorestv.football.domain;

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
 * Maç (fikstür). API-Football'un fixture ID'si birincil anahtar olarak kullanılır.
 *
 * <p>Anasayfa fikstür listesi ve canlı skor aynı satırdan beslenir:
 * {@code statusShort} maçın durumunu, {@code elapsed} canlı dakikayı,
 * {@code homeGoals}/{@code awayGoals} anlık skoru tutar. {@code lastSyncedAt}
 * ise frontend'in canlı saati pürüzsüz saydırması için referans noktasıdır.
 */
@Entity
@Table(name = "fixtures")
@Getter
@Setter
@NoArgsConstructor
public class Fixture {

    /** API-Football fixture id (atanmış; üretilmez). */
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    /** Sezon yılı (örn. 2025). */
    @Column(nullable = false)
    private Integer season;

    /** Tur bilgisi, örn. "Regular Season - 12". */
    @Column(length = 100)
    private String round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    /**
     * FK kolonlarının SALT-OKUNUR eşlemesi (yazma {@link #homeTeam}/{@link #awayTeam}
     * ilişkisinden gider; bu alanlar insertable/updatable=false).
     *
     * <p>NEDEN: Takım sayfası sorgularında filtreyi {@code f.homeTeam.id} ile
     * yazınca, JOIN FETCH f.homeTeam yüzünden Hibernate koşulu join'lenen teams
     * alias'ına ({@code ht.id}) bindliyordu; bu da {@code idx_fixtures_home_team_id}
     * / {@code idx_fixtures_away_team_id} index'lerini devre dışı bırakıp fixtures
     * FULL-SCAN'e (1-1.7 sn) yol açıyordu. Bu alanlarla ({@code f.homeTeamId})
     * koşul DOĞRUDAN {@code f.home_team_id} FK kolonuna biner → BitmapOr index →
     * sadece o takımın maçları okunur (~ms).
     */
    @Column(name = "home_team_id", insertable = false, updatable = false)
    private Long homeTeamId;

    @Column(name = "away_team_id", insertable = false, updatable = false)
    private Long awayTeamId;

    /** Maçın oynandığı stadyum (opsiyonel, FK). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    /**
     * Inline stadyum adı (FK olmayan fallback). API {@code venue.id} null
     * gönderdiğinde ama isim+şehir verdiğinde burada saklarız —
     * {@link Venue} entity'siyle FK kuramadığımız için.
     */
    @Column(name = "venue_name", length = 255)
    private String venueName;

    @Column(name = "venue_city", length = 100)
    private String venueCity;

    @Column(length = 120)
    private String referee;

    /** Maç başlangıç zamanı. */
    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    /** Kısa durum kodu: NS, 1H, HT, 2H, FT, PST, CANC... */
    @Column(name = "status_short", nullable = false, length = 10)
    private String statusShort;

    @Column(name = "status_long", length = 50)
    private String statusLong;

    /** Canlı maçta geçen dakika; maç başlamadıysa null. */
    private Integer elapsed;

    /** Uzatma dakikası (örn. 90+3 → 3); status.extra alanından gelir, yoksa null. */
    @Column(name = "status_extra")
    private Integer statusExtra;

    @Column(name = "home_goals")
    private Integer homeGoals;

    @Column(name = "away_goals")
    private Integer awayGoals;

    @Column(name = "score_ht_home")
    private Integer scoreHtHome;

    @Column(name = "score_ht_away")
    private Integer scoreHtAway;

    @Column(name = "score_ft_home")
    private Integer scoreFtHome;

    @Column(name = "score_ft_away")
    private Integer scoreFtAway;

    @Column(name = "score_et_home")
    private Integer scoreEtHome;

    @Column(name = "score_et_away")
    private Integer scoreEtAway;

    @Column(name = "score_pen_home")
    private Integer scorePenHome;

    @Column(name = "score_pen_away")
    private Integer scorePenAway;

    /** Bu maç API'den en son ne zaman senkronlandı. */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /**
     * "Başladı" push'unun gönderildiği an (TAM-BİR-KEZ kapısı). NULL = henüz
     * gönderilmedi. FixtureNotifyGate atomik UPDATE ile set eder; FixtureUpserter
     * bu alana DOKUNMAZ, yani sync'ler boyunca korunur.
     */
    @Column(name = "notif_kickoff_at")
    private Instant notifKickoffAt;

    /** "Bitti" push'unun gönderildiği an (TAM-BİR-KEZ kapısı). NULL = gönderilmedi. */
    @Column(name = "notif_final_at")
    private Instant notifFinalAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

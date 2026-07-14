package com.scorestv.mobile.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Garantili teslim için bildirim OUTBOX satırı.
 *
 * <p>Maç durumu geçişi tam-bir-kez claim edildiğinde bir PENDING satır yazılır;
 * {@link NotificationOutboxWorker} bunu gönderir, başarısızsa backoff'la tekrar
 * dener. Böylece FCM/restart/ağ hatası bildirimi düşürmez.
 */
@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String KIND_KICKOFF = "KICKOFF";
    public static final String KIND_FINAL = "FINAL";
    public static final String KIND_GOAL = "GOAL";
    public static final String KIND_EVENT = "EVENT";
    public static final String KIND_LINEUP = "LINEUP";
    public static final String KIND_HALFTIME = "HALFTIME";
    public static final String KIND_SECONDHALF = "SECONDHALF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String kind;

    /** Alıcı tercih tipi: basladi|bitti|gol|kirmizi|penalti. */
    @Column(name = "notif_type", nullable = false, length = 20)
    private String notifType;

    @Column(name = "fixture_id", nullable = false)
    private Long fixtureId;

    /** GOAL/EVENT: ilgili takım; KICKOFF/FINAL: null (iki takım da alıcı). */
    @Column(name = "team_id")
    private Long teamId;

    /** Render edilmiş mesaj başlığı (enqueue anında dondurulur). */
    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** EN başlık (yerelleştirme). null → gönderimde TR'ye düşülür. */
    @Column(name = "title_en", length = 255)
    private String titleEn;

    /** EN gövde (yerelleştirme). null → gönderimde TR'ye düşülür. */
    @Column(name = "body_en", length = 500)
    private String bodyEn;

    /** FCM data payload (JSON string). */
    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    @Column(name = "dedup_key", nullable = false, length = 120)
    private String dedupKey;

    /** OS bildirim slotu — aynı collapse_key yeni bildirimi YERİNDE günceller
     * (Android notification tag / APNs apns-collapse-id). null → normal bildirim. */
    @Column(name = "collapse_key", length = 120)
    private String collapseKey;

    /** true → sessiz güncelleme (ses/titreşim yok). İki fazlı bildirimde
     * isimsiz→isimli güncellemeyi kullanıcıyı tekrar titretmeden yapar. */
    @Column(nullable = false)
    private boolean silent = false;

    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

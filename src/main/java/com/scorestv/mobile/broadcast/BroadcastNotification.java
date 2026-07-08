package com.scorestv.mobile.broadcast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Gonderilmis genel (broadcast) push bildiriminin gecmis kaydi.
 * Habere/maca bagli DEGIL — admin panelinden serbest metinle gonderilir.
 */
@Entity
@Table(name = "broadcast_notifications")
public class BroadcastNotification {

    /** Kuyrukta, gonderilmeyi bekliyor. */
    public static final String STATUS_QUEUED = "QUEUED";
    /** Basariyla (en az bir cihaza) iletildi. */
    public static final String STATUS_SENT = "SENT";
    /** Maksimum denemeye ragmen iletilemedi. */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    /** Opsiyonel — dokununca acilacak URL (http/https) ya da uygulama ici yol (/...). */
    @Column(length = 1000)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_target", nullable = false, length = 20)
    private BroadcastPlatform platformTarget = BroadcastPlatform.ALL;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang_target", nullable = false, length = 20)
    private BroadcastLang langTarget = BroadcastLang.ALL;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    /** Garantili teslim durumu: QUEUED | SENT | FAILED. */
    @Column(nullable = false, length = 20)
    private String status = STATUS_QUEUED;

    /** Kac kez denendi (backoff + max-deneme icin). */
    @Column(nullable = false)
    private int attempts;

    /** Bir sonraki deneme zamani (worker bunu <= now olan satirlari alir). */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    /** Son hata mesaji (varsa) — teshis icin. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public BroadcastPlatform getPlatformTarget() {
        return platformTarget;
    }

    public void setPlatformTarget(BroadcastPlatform platformTarget) {
        this.platformTarget = platformTarget;
    }

    public BroadcastLang getLangTarget() {
        return langTarget;
    }

    public void setLangTarget(BroadcastLang langTarget) {
        this.langTarget = langTarget;
    }

    public int getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(int recipientCount) {
        this.recipientCount = recipientCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public void setSentCount(int sentCount) {
        this.sentCount = sentCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

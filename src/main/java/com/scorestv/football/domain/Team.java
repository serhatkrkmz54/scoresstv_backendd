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
 * Takım. API-Football'un team ID'si birincil anahtar olarak kullanılır.
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team implements TranslatableName {

    /** API-Football team id (atanmış; üretilmez). */
    @Id
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Elle girilen Türkçe takım adı (örn. "Bayern Munich" → "Bayern Münih");
     * henüz çevrilmemişse null. Senkron upsert'i bu alana DOKUNMAZ.
     */
    @Column(name = "name_tr", length = 150)
    private String nameTr;

    @Column(length = 10)
    private String code;

    @Column(length = 100)
    private String country;

    private Integer founded;

    /** Milli takım mı? */
    @Column(nullable = false)
    private boolean national;

    /**
     * Kapsam (oncelikli takim mi?). Periyodik joblar (DailyTeamRefreshJob)
     * sadece covered=true takimlari tazeler. ADMIN secimi.
     */
    @Column(nullable = false)
    private boolean covered;

    /**
     * ADMIN manuel bas antrenor override'i. Set ediliyse picker direkt bu
     * coach'u kullanir; lineup/rule kontrolune dusmez. Off-season gibi
     * otomatik sinyallerin gucsuz oldugu durumlar icin guvenlik agi.
     * Null = override yok, otomatik picker calisir.
     */
    @Column(name = "head_coach_override_id")
    private Long headCoachOverrideId;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    /** MinIO'da aynalanan logonun nesne anahtarı; aynalanana dek null. */
    @Column(name = "logo_key", length = 255)
    private String logoKey;

    /** Takımın ev sahibi olduğu stadyum (opsiyonel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

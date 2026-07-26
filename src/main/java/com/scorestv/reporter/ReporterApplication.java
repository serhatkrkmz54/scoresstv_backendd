package com.scorestv.reporter;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Saha muhabiri başvurusu — "X ligini ben girerim". */
@Entity
@Table(name = "reporter_applications")
@Getter
@Setter
@NoArgsConstructor
public class ReporterApplication extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "league_name", nullable = false, length = 150)
    private String leagueName;

    @Column(length = 150)
    private String region;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** Onayda oluşturulan manuel ligin id'si. */
    @Column(name = "league_id")
    private Long leagueId;
}

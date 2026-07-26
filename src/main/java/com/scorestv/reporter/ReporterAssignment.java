package com.scorestv.reporter;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Muhabir ↔ manuel lig ataması. Yetki kontrolü bu tablodan yapılır. */
@Entity
@Table(name = "reporter_assignments")
@Getter
@Setter
@NoArgsConstructor
public class ReporterAssignment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    @Column(nullable = false)
    private boolean active = true;
}

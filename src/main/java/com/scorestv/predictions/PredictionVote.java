package com.scorestv.predictions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Bir maça verilen sonuç tahmini oyu (anonim). {@code voterId} anonim istemci
 * kimliği (cihaz/tarayıcı başına üretilen UUID); (matchId, sport, voterId)
 * benzersizdir → kullanıcı/cihaz başına tek oy, kickoff'a kadar değiştirilebilir.
 */
@Entity
@Table(
        name = "fixture_prediction_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "fixture_prediction_votes_unique",
                columnNames = {"match_id", "sport", "voter_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class PredictionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Futbolda Fixture id (basketbolda BasketballGame id). */
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    /** "FOOTBALL" veya "BASKETBALL". */
    @Column(name = "sport", nullable = false, length = 20)
    private String sport;

    /** Anonim istemci kimliği (cihaz/tarayıcı UUID). */
    @Column(name = "voter_id", nullable = false, length = 64)
    private String voterId;

    /** "HOME" / "DRAW" / "AWAY". */
    @Column(name = "choice", nullable = false, length = 8)
    private String choice;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

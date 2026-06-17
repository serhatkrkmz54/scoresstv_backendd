package com.scorestv.predictions;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** {@link PredictionVote} lookup + dağılım sayımı. */
public interface PredictionVoteRepository
        extends JpaRepository<PredictionVote, Long> {

    /** Bu oylayanın bu maça verdiği oy (varsa) — upsert + kendi seçimi. */
    Optional<PredictionVote> findByMatchIdAndSportAndVoterId(
            Long matchId, String sport, String voterId);

    /**
     * Maç + sport için choice bazında oy sayıları.
     * Dönüş: her satır {@code [choice(String), count(Long)]}.
     */
    @Query("SELECT v.choice, COUNT(v) FROM PredictionVote v "
            + "WHERE v.matchId = :matchId AND v.sport = :sport "
            + "GROUP BY v.choice")
    List<Object[]> countByChoice(@Param("matchId") Long matchId,
                                 @Param("sport") String sport);
}

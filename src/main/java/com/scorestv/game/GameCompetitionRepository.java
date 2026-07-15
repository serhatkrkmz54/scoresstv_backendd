package com.scorestv.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GameCompetitionRepository extends JpaRepository<GameCompetition, Long> {

    /** Belirli durumdaki yarışmalar (kilit anına göre). */
    List<GameCompetition> findByStatusOrderByLockAtAsc(GameStatus status);

    /** Bir kapsamın (WEEKLY vb.) en güncel açık yarışması — kullanıcıya gösterilecek. */
    Optional<GameCompetition> findFirstByScopeAndStatusOrderByStartAtDesc(
            GameScope scope, GameStatus status);

    /** Çözümleme adayları: dönemi bitmiş ama henüz RESOLVED olmayanlar. */
    List<GameCompetition> findByStatusInAndEndAtLessThanEqual(
            List<GameStatus> statuses, Instant now);
}

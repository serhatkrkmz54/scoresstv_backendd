package com.scorestv.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamePickRepository extends JpaRepository<GamePick, Long> {

    Optional<GamePick> findByDuelIdAndUserId(Long duelId, Long userId);

    boolean existsByDuelIdAndUserId(Long duelId, Long userId);

    List<GamePick> findByCompetitionIdAndUserId(Long competitionId, Long userId);

    /** Bir yarismanin TUM tahminleri — cozumleme (difficulty + streak) icin. */
    List<GamePick> findByCompetitionId(Long competitionId);

    /** Yarismadaki her duello icin A/B tahmin sayilari (canli dagilim). */
    @Query("SELECT p.duelId AS duelId, p.pick AS pick, COUNT(p) AS cnt "
            + "FROM GamePick p WHERE p.competitionId = :cid GROUP BY p.duelId, p.pick")
    List<DuelPickCount> pickCounts(@Param("cid") Long competitionId);

    /** Bir düelloya tahmin veren tüm kullanıcılar — çözümlemede coin dağıtmak için. */
    List<GamePick> findByDuelId(Long duelId);

    /** Yarışma sıralaması: kullanıcı başına toplam coin + doğru sayısı. */
    @Query("SELECT p.userId AS userId, SUM(p.coinsAwarded) AS coins, "
            + "SUM(CASE WHEN p.correct = true THEN 1 ELSE 0 END) AS correctCount, "
            + "COUNT(p) AS total "
            + "FROM GamePick p WHERE p.competitionId = :competitionId "
            + "GROUP BY p.userId ORDER BY SUM(p.coinsAwarded) DESC")
    List<CompetitionLeaderboardRow> leaderboard(
            @Param("competitionId") Long competitionId, Pageable pageable);
}

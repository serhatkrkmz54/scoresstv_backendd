package com.scorestv.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameDuelRepository extends JpaRepository<GameDuel, Long> {

    List<GameDuel> findByCompetitionIdOrderBySortOrderAsc(Long competitionId);

    List<GameDuel> findByCompetitionIdAndStatus(Long competitionId, DuelStatus status);
}

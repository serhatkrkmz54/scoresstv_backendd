package com.scorestv.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoresCoinLedgerRepository extends JpaRepository<ScoresCoinLedger, Long> {

    /** Kullanıcının cüzdan hareketleri (yeniden eskiye). */
    List<ScoresCoinLedger> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

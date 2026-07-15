package com.scorestv.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGameStatRepository extends JpaRepository<UserGameStat, Long> {

    /** Genel (all-time) sıralama — kazanılan toplam coin'e göre. */
    List<UserGameStat> findAllByOrderByLifetimeCoinsDesc(Pageable pageable);
}

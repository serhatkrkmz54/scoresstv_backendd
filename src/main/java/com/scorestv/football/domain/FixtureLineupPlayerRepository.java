package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Kadro oyuncularına erişim. */
public interface FixtureLineupPlayerRepository
        extends JpaRepository<FixtureLineupPlayer, Long> {

    /** Bir kadronun tüm oyuncuları, API'den gelen sırada. */
    List<FixtureLineupPlayer> findByLineupIdOrderBySortOrderAsc(Long lineupId);

    /**
     * Birden çok kadro için oyuncuları tek sorguda getirir (N+1 önler).
     * Sıralama: önce lineup_id, sonra API'den gelen sıralama.
     */
    List<FixtureLineupPlayer> findByLineupIdInOrderByLineupIdAscSortOrderAsc(
            Collection<Long> lineupIds);

    /** Bir kadronun TÜM oyuncularını siler — replace pattern için. */
    void deleteByLineupId(Long lineupId);
}

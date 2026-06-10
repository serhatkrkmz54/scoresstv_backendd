package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Lig sezonu verisine erişim. */
public interface SeasonRepository extends JpaRepository<Season, Long> {

    /** Belirli bir ligin belirli yıldaki sezonu — senkron upsert anahtarı. */
    Optional<Season> findByLeagueIdAndYear(Long leagueId, Integer year);

    /** Bir ligin tüm sezonları, en yeni yıl önce. */
    List<Season> findByLeagueIdOrderByYearDesc(Long leagueId);

    /**
     * Tüm sezonlar, ligleri birlikte yüklenmiş (JOIN FETCH ile N+1 önlenir).
     * Takım arşiv senkronu her (lig, sezon) çiftini bununla gezer.
     */
    @Query("SELECT s FROM Season s JOIN FETCH s.league ORDER BY s.league.id, s.year")
    List<Season> findAllWithLeague();
}

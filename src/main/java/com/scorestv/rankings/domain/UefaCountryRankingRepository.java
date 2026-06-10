package com.scorestv.rankings.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** UEFA milli takim katsayisi DB erisimi. */
public interface UefaCountryRankingRepository extends JpaRepository<UefaCountryRanking, Long> {

    /** Hedef sezon icin tum siralama. */
    List<UefaCountryRanking> findByTargetSeasonYearOrderByRankAsc(Integer targetSeasonYear);

    /** DB'de mevcut en buyuk hedef sezon. */
    @Query("SELECT MAX(c.targetSeasonYear) FROM UefaCountryRanking c")
    Integer findLatestTargetSeasonYear();

    /** Belirli hedef sezona ait tum satirlari sil — REPLACE icin. */
    @Modifying
    @Query("DELETE FROM UefaCountryRanking c WHERE c.targetSeasonYear = :year")
    int deleteByTargetSeasonYear(@Param("year") Integer year);
}

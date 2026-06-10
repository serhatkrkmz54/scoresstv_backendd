package com.scorestv.rankings.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** UEFA kulup katsayisi DB erisimi. */
public interface UefaClubRankingRepository extends JpaRepository<UefaClubRanking, Long> {

    /** Hedef sezon icin tum siralama. */
    List<UefaClubRanking> findByTargetSeasonYearOrderByRankAsc(Integer targetSeasonYear);

    /** Hedef sezon + ulke kodu filtreli. */
    List<UefaClubRanking> findByTargetSeasonYearAndCountryCodeOrderByRankAsc(
            Integer targetSeasonYear, String countryCode);

    /** DB'de mevcut en buyuk hedef sezon (UI default secimi). */
    @Query("SELECT MAX(c.targetSeasonYear) FROM UefaClubRanking c")
    Integer findLatestTargetSeasonYear();

    /** Belirli hedef sezona ait tum satirlari sil — REPLACE icin. */
    @Modifying
    @Query("DELETE FROM UefaClubRanking c WHERE c.targetSeasonYear = :year")
    int deleteByTargetSeasonYear(@Param("year") Integer year);
}

package com.scorestv.rankings.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** FIFA milli takim siralamasi DB erisimi. */
public interface FifaRankingRepository extends JpaRepository<FifaRanking, Long> {

    /** Tum siralama, 1. sıradan baslayarak. */
    List<FifaRanking> findAllByOrderByRankAsc();

    /** Belirli konfederasyon (UEFA, CONMEBOL, ...) siralamasi. */
    List<FifaRanking> findByConfederationOrderByRankAsc(String confederation);

    /** REPLACE — sync icin tumunu sil. */
    @Modifying
    @Query("DELETE FROM FifaRanking f")
    int deleteAllRows();
}

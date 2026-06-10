package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Coach kupa kayitlarina erisim. */
public interface CoachTrophyRepository extends JpaRepository<CoachTrophy, Long> {

    @Query("SELECT t FROM CoachTrophy t WHERE t.coach.id = :coachId "
            + "ORDER BY t.season DESC NULLS LAST, t.place ASC")
    List<CoachTrophy> findByCoachIdOrderBySeason(@Param("coachId") Long coachId);

    /** Replace: kupa listesi tek seferde tazelenir. */
    @Modifying
    @Query("DELETE FROM CoachTrophy t WHERE t.coach.id = :coachId")
    void deleteByCoachId(@Param("coachId") Long coachId);
}

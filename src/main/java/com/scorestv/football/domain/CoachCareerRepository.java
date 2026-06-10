package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Coach kariyer kayitlarina erisim. */
public interface CoachCareerRepository extends JpaRepository<CoachCareer, Long> {

    /** Bir kocun tum kariyeri, yeni → eski. */
    @Query("SELECT c FROM CoachCareer c "
            + "WHERE c.coach.id = :coachId "
            + "ORDER BY c.startDate DESC NULLS LAST")
    List<CoachCareer> findByCoachIdOrderByStartDateDesc(@Param("coachId") Long coachId);

    /**
     * Bir kocun yalnizca BU takimdaki donemleri — takim sayfasi "Tekik direktor"
     * widget'i icin. start_date yeni → eski.
     */
    @Query("SELECT c FROM CoachCareer c "
            + "WHERE c.coach.id = :coachId AND c.teamId = :teamId "
            + "ORDER BY c.startDate DESC NULLS LAST")
    List<CoachCareer> findByCoachIdAndTeamId(
            @Param("coachId") Long coachId, @Param("teamId") Long teamId);

    /** Replace: bir kocun kariyerini sifirla, gelen tam set yazilsin. */
    @Modifying
    @Query("DELETE FROM CoachCareer c WHERE c.coach.id = :coachId")
    void deleteByCoachId(@Param("coachId") Long coachId);
}

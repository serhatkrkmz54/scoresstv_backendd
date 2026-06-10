package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Tekik direktor master tablosuna erisim. */
public interface CoachRepository extends JpaRepository<Coach, Long> {

    /** Bir takimin mevcut kocu (yalnizca bir kayit beklenir — sync logic'i garantiler). */
    Optional<Coach> findByCurrentTeamId(Long currentTeamId);

    /** ImageMirrorService.mirrorCoachPhotos icin: foto henuz aynalanmamis. */
    List<Coach> findTop200ByPhotoKeyIsNullAndPhotoUrlIsNotNull();

    /**
     * Bir takim icin {@code current_team_id} alanini tasiyan TUM coach'larin
     * isaretini temizler (NULL'a indir). Coach sync'i once bunu cagirir,
     * sonra dogru bas antrenor icin {@code setCurrentTeamId} yapar — bir
     * takimda ayni anda yalnizca bir coach "mevcut" olarak isaretlenir.
     */
    @Modifying
    @Query("UPDATE Coach c SET c.currentTeamId = NULL WHERE c.currentTeamId = :teamId")
    int clearCurrentTeam(@Param("teamId") Long teamId);
}

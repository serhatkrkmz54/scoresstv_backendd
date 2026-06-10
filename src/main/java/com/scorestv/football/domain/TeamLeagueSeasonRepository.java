package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link TeamLeagueSeason} junction tablosu icin sorgular.
 *
 * <p>Birincil kullanim: "lig X sezon Y'deki tum takimlar" — onboarding'de
 * favori takim secimi icin. Sonuc Team entity'lerinin tamami doner.
 */
public interface TeamLeagueSeasonRepository
        extends JpaRepository<TeamLeagueSeason, TeamLeagueSeason.Pk> {

    /**
     * Verilen lig+sezona kayitli tum takimlar. Junction'da kayit yoksa
     * sonuc bos liste — caller fallback chain'e dusebilir.
     *
     * <p>Sonuc ada gore alfabetik siralanir (UI'da tutarli olsun).
     */
    @Query("SELECT t FROM Team t WHERE t.id IN ("
            + "  SELECT tls.id.teamId FROM TeamLeagueSeason tls "
            + "  WHERE tls.id.leagueId = :leagueId AND tls.id.season = :season"
            + ") ORDER BY t.name")
    List<Team> findTeamsByLeagueAndSeason(
            @Param("leagueId") Long leagueId, @Param("season") Integer season);

    /**
     * Bu lig+sezon icin junction'da hic kayit var mi? Hic kayit yoksa
     * /teams sync hic tetiklenmemis demek — caller bunu tetikleyebilir.
     */
    boolean existsByIdLeagueIdAndIdSeason(Long leagueId, Integer season);
}

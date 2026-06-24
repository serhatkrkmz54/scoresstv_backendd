package com.scorestv.volleyball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Voleybol takim-lig-sezon junction repo. Hizli sorgu (lig+sezon → takimlar)
 * + replace pattern (eski kadroyu temizle, yeniyi yaz).
 */
public interface VolleyballTeamLeagueSeasonRepository
        extends JpaRepository<VolleyballTeamLeagueSeason, VolleyballTeamLeagueSeason.Pk> {

    /** Lig+sezon kadrosundaki takim id'leri. */
    @Query("""
        SELECT j.id.teamId FROM VolleyballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    List<Long> findTeamIdsByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                            @Param("season") String season);

    /** Lig+sezon kadrosunda en az 1 takim var mi? */
    @Query("""
        SELECT COUNT(j) > 0 FROM VolleyballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    boolean existsAnyByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                       @Param("season") String season);

    /** Bir lig+sezonun en son sync zamani (null = hic sync yok). */
    @Query("""
        SELECT MAX(j.syncedAt) FROM VolleyballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    Instant findLastSyncedAt(@Param("leagueId") Long leagueId,
                             @Param("season") String season);

    /** Bir lig+sezonun TUM junction kayitlarini sil — replace sync icin. */
    @Modifying
    @Query("""
        DELETE FROM VolleyballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    int deleteByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                @Param("season") String season);
}

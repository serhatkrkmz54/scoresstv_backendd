package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Basketbol takım-lig-sezon junction repo. Hızlı sorgu (lig+sezon → takımlar)
 * + replace pattern (eski kadroyu temizle, yeniyi yaz).
 */
public interface BasketballTeamLeagueSeasonRepository
        extends JpaRepository<BasketballTeamLeagueSeason, BasketballTeamLeagueSeason.Pk> {

    /** Lig+sezon kadrosundaki takımlar (BasketballTeam join'siz, sadece id'ler). */
    @Query("""
        SELECT j.id.teamId FROM BasketballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    List<Long> findTeamIdsByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                             @Param("season") String season);

    /** Lig+sezon kadrosunda en az 1 takım var mı? */
    @Query("""
        SELECT COUNT(j) > 0 FROM BasketballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    boolean existsAnyByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                        @Param("season") String season);

    /** Bir lig+sezonun en son sync zamanı (null = hiç sync yok). */
    @Query("""
        SELECT MAX(j.syncedAt) FROM BasketballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    Instant findLastSyncedAt(@Param("leagueId") Long leagueId,
                              @Param("season") String season);

    /** Bir lig+sezonun TÜM junction kayıtlarını sil — replace sync için. */
    @Modifying
    @Query("""
        DELETE FROM BasketballTeamLeagueSeason j
        WHERE j.id.leagueId = :leagueId AND j.id.season = :season
        """)
    int deleteByLeagueAndSeason(@Param("leagueId") Long leagueId,
                                 @Param("season") String season);

    /** Bir takımın oynadığı lig+sezon listesi (ileride team detay sayfasında). */
    @Query("""
        SELECT j.id.leagueId, j.id.season FROM BasketballTeamLeagueSeason j
        WHERE j.id.teamId = :teamId
        """)
    List<Object[]> findLeaguesForTeam(@Param("teamId") Long teamId);
}

package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Puan durumu verisine erişim. */
public interface StandingRepository extends JpaRepository<Standing, Long> {

    /** Bir ligin belirli sezonundaki puan durumu, sıraya göre (1 = lider). */
    List<Standing> findByLeagueIdAndSeasonOrderByRankAsc(Long leagueId, Integer season);

    /**
     * Bir ligin belirli sezonundaki tüm puan durumu satırlarını siler.
     * Senkronda tablo silinip yeniden yazıldığı için kullanılır.
     * Çağıran metot {@code @Transactional} olmalıdır.
     */
    void deleteByLeagueIdAndSeason(Long leagueId, Integer season);

    /**
     * Takim sayfasi icin: belirli takimin verilen sezonda yer aldigi liglerdeki
     * sirasi. Birden cok lig olabilir (bir takim ulkesinde 2 ligde de oynayabilir —
     * lig + kupa vs).
     */
    @Query("SELECT s FROM Standing s "
            + "JOIN FETCH s.league "
            + "WHERE s.team.id = :teamId AND s.season = :season")
    List<Standing> findByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);

    /**
     * Bir lig+sezonda kac farkli grup var? UI'da takim sayfasinda groupName
     * gostermeyi karar etmek icin: tek-grup ligin (ulusal lig) groupName'i
     * suppress edilir; cok grupludaysa (CL grup asamasi vb.) gosterilir.
     */
    @Query("SELECT COUNT(DISTINCT s.groupName) FROM Standing s "
            + "WHERE s.league.id = :leagueId AND s.season = :season")
    long countDistinctGroupNamesByLeagueAndSeason(
            @Param("leagueId") Long leagueId, @Param("season") Integer season);

    /**
     * Standings'te kayitli bu lig+sezondaki tum takimlar — ada gore siralanmis.
     * Gruplu turnuvalarda ayni takim cok kez kayitli olabilir; subquery ile teklenir.
     *
     * <p>{@code LeagueTeamsService} icin standings-based fallback. Standings
     * sync calistirilan ligler icin fixtures'tan daha kapsamli (sezon basinda
     * fikstur olmasa bile takim listesi resmi olarak elde edilir).
     *
     * <p>Subquery + outer SELECT pattern'i: JPQL'de DISTINCT ile ORDER BY
     * alaninin SELECT'te bulunmasi gerekir; outer query Team'i baz aldigi icin
     * t.name'e gore siralama temiz calisir.
     */
    @Query("SELECT t FROM Team t WHERE t.id IN ("
            + "  SELECT s.team.id FROM Standing s "
            + "  WHERE s.league.id = :leagueId AND s.season = :season"
            + ") ORDER BY t.name")
    List<Team> findDistinctTeamsByLeagueAndSeason(
            @Param("leagueId") Long leagueId, @Param("season") Integer season);
}

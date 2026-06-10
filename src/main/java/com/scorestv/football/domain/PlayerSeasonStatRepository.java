package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Oyuncu sezonluk istatistik kayitlarina erisim. */
public interface PlayerSeasonStatRepository extends JpaRepository<PlayerSeasonStat, Long> {

    /**
     * Bir takimin belirli sezondaki TUM oyuncu istatistikleri (tum ligler).
     * Lig FK JOIN FETCH ile gelir — takim sayfasi her satirda lig adi/logo'su
     * gostermek icin (CL vs Lig ayrimi).
     */
    @Query("SELECT s FROM PlayerSeasonStat s "
            + "JOIN FETCH s.league "
            + "WHERE s.team.id = :teamId AND s.season = :season "
            + "ORDER BY s.playerId, s.league.id")
    List<PlayerSeasonStat> findByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);

    /**
     * Replace pattern: bir takim+sezonun TUM oyuncu istatistiklerini siler.
     * Sonraki upsert tam set yazar. UNIQUE constraint yarismasini onler
     * (sayfalama sirasinda paginate edilen sayfalar arasi).
     */
    @Modifying
    @Query("DELETE FROM PlayerSeasonStat s "
            + "WHERE s.team.id = :teamId AND s.season = :season")
    void deleteByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);

    /** Empty-check: takim+sezon icin hic kayit var mi? Lazy sync icin. */
    @Query("SELECT COUNT(s) FROM PlayerSeasonStat s "
            + "WHERE s.team.id = :teamId AND s.season = :season")
    long countByTeamIdAndSeason(
            @Param("teamId") Long teamId, @Param("season") Integer season);

    // ============================================================
    // Player detay sayfasi sorgulari
    // ============================================================

    /**
     * Bir oyuncunun TUM kariyer istatistikleri — sezon dropdown + tablo icin.
     * Lig JOIN FETCH (UI lig adi/logo'su gosterir), takim de gerekli.
     */
    @Query("SELECT s FROM PlayerSeasonStat s "
            + "JOIN FETCH s.league "
            + "JOIN FETCH s.team "
            + "WHERE s.playerId = :playerId "
            + "ORDER BY s.season DESC, s.league.id")
    List<PlayerSeasonStat> findByPlayerId(@Param("playerId") Long playerId);

    /** Bir oyuncunun belirli sezondaki TUM istatistikleri (turnuva basina ayri). */
    @Query("SELECT s FROM PlayerSeasonStat s "
            + "JOIN FETCH s.league "
            + "JOIN FETCH s.team "
            + "WHERE s.playerId = :playerId AND s.season = :season")
    List<PlayerSeasonStat> findByPlayerIdAndSeason(
            @Param("playerId") Long playerId, @Param("season") Integer season);

    /**
     * REPLACE pattern (player+season): bir oyuncunun belirli sezondaki tum
     * kayitlarini siler. PlayerProfileSyncService oyuncu bazli sync'ten
     * once cagirir.
     */
    @Modifying
    @Query("DELETE FROM PlayerSeasonStat s "
            + "WHERE s.playerId = :playerId AND s.season = :season")
    void deleteByPlayerIdAndSeason(
            @Param("playerId") Long playerId, @Param("season") Integer season);

    /** Empty-check: oyuncu+sezon icin hic kayit var mi? Player lazy sync icin. */
    @Query("SELECT COUNT(s) FROM PlayerSeasonStat s "
            + "WHERE s.playerId = :playerId AND s.season = :season")
    long countByPlayerIdAndSeason(
            @Param("playerId") Long playerId, @Param("season") Integer season);

    /** DB'de oyuncu icin var olan tum sezon yillari (yeni → eski). */
    @Query("SELECT DISTINCT s.season FROM PlayerSeasonStat s "
            + "WHERE s.playerId = :playerId "
            + "ORDER BY s.season DESC")
    List<Integer> findSeasonYearsByPlayer(@Param("playerId") Long playerId);

    /**
     * Belirli bir lig + sezonda en yuksek rating'li oyuncular (top-N).
     *
     * <p>Postgres JSONB: rating, {@code stats_json -> 'games' ->> 'rating'}
     * altinda string olarak tutulur (orn. "7.85"). CAST AS NUMERIC ile
     * sayisal sirala. Minimum {@code min_appearances} oynamis oyuncularla
     * sinirli — yoksa 1 mac oynamis bir oyuncu 10 rating ile basa cikar.
     *
     * <p>Native query — JPA bu JSONB operatorlerini ifade edemez. Standings
     * sayfasinin "rating'e gore en iyi 20 oyuncu" widget'i icin.
     */
    @Query(value = "SELECT s.* FROM player_season_stats s "
            + "WHERE s.league_id = :leagueId "
            + "  AND s.season = :season "
            + "  AND (s.stats_json -> 'games' ->> 'appearences')::INT >= :minAppearances "
            + "  AND s.stats_json -> 'games' ->> 'rating' IS NOT NULL "
            + "ORDER BY (s.stats_json -> 'games' ->> 'rating')::NUMERIC DESC NULLS LAST "
            + "LIMIT :limit",
            nativeQuery = true)
    List<PlayerSeasonStat> findTopRatedByLeagueSeason(
            @Param("leagueId") Long leagueId,
            @Param("season") Integer season,
            @Param("minAppearances") int minAppearances,
            @Param("limit") int limit);

    /**
     * Atomik upsert — Postgres native ON CONFLICT DO UPDATE.
     *
     * <p>Mevcut JPA {@code repository.save()} race condition'da
     * (UNIQUE constraint violation) Hibernate persistence context'i kirletiyor
     * ve {@code DataIntegrityViolationException} catch edilse bile commit
     * sirasinda tx-poisoning yasaniyor. Bu native upsert:
     * <ul>
     *   <li>Race-safe: ON CONFLICT atomik update yapar.</li>
     *   <li>Tx-poisoning yok: PG kendisi resolve eder, JPA persistence context
     *       devreye girmez.</li>
     *   <li>WARN log'lari kalkar.</li>
     * </ul>
     *
     * <p><b>JSONB:</b> {@code statsJson} parametresi {@link String} olarak gelir
     * (Jackson tarafindan serialize edilmis). Native query <code>?::jsonb</code>
     * cast ile veritabani tarafinda parse eder.
     *
     * @return 1 (her zaman — ON CONFLICT da update sayar)
     */
    @Modifying
    @Query(value = """
            INSERT INTO player_season_stats
                (player_id, team_id, league_id, season, stats_json,
                 created_at, updated_at)
            VALUES
                (:playerId, :teamId, :leagueId, :season,
                 CAST(:statsJson AS jsonb), NOW(), NOW())
            ON CONFLICT ON CONSTRAINT uq_player_season_stats DO UPDATE
            SET stats_json = EXCLUDED.stats_json,
                updated_at = NOW()
            """, nativeQuery = true)
    int upsertNative(@Param("playerId") Long playerId,
                     @Param("teamId") Long teamId,
                     @Param("leagueId") Long leagueId,
                     @Param("season") Integer season,
                     @Param("statsJson") String statsJson);
}

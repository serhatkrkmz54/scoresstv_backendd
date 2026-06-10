package com.scorestv.football.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Transfer (flat) kayitlarina erisim. */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /**
     * Bir takima ait tum hareketler (gelen + giden), tarih sirali yeni-eski.
     * Pageable ile sinir.
     */
    @Query("SELECT t FROM Transfer t "
            + "WHERE t.inTeamId = :teamId OR t.outTeamId = :teamId "
            + "ORDER BY t.transferDate DESC NULLS LAST")
    List<Transfer> findByTeam(@Param("teamId") Long teamId, Pageable pageable);

    /** Takim sayfasi sayim: empty-check icin. */
    @Query("SELECT COUNT(t) FROM Transfer t "
            + "WHERE t.inTeamId = :teamId OR t.outTeamId = :teamId")
    long countByTeam(@Param("teamId") Long teamId);

    /** Bir oyuncunun tum kariyer transferleri (oyuncu detay sayfasi icin). */
    List<Transfer> findByPlayerIdOrderByTransferDateDesc(Long playerId);

    /**
     * Upserter pre-check: ayni transfer kayitli mi? UNIQUE constraint
     * (player_id, transfer_date, in_team_id, out_team_id) icin. Dup save()
     * yerine bu sorguyla once kontrol edilirse tx poisoning olmaz.
     *
     * <p><b>Neden native + IS NOT DISTINCT FROM:</b> JPQL'in NULL handling'i
     * PostgreSQL JDBC tip cikarimi ile catisiyor — JDBC NULL parametresinin
     * tipini cikartmiyor, hata: "could not determine data type of parameter".
     * Native query + Postgres'in {@code IS NOT DISTINCT FROM} operatoru
     * NULL-guvenli esitlik saglar ve CAST'ler tip cikarimi sorununu kapatir.
     */
    @Query(value = "SELECT EXISTS("
            + "SELECT 1 FROM transfers "
            + "WHERE player_id = :playerId "
            + "  AND transfer_date IS NOT DISTINCT FROM CAST(:date AS DATE) "
            + "  AND in_team_id IS NOT DISTINCT FROM CAST(:inTeamId AS BIGINT) "
            + "  AND out_team_id IS NOT DISTINCT FROM CAST(:outTeamId AS BIGINT))",
            nativeQuery = true)
    boolean existsUnique(@Param("playerId") Long playerId,
                         @Param("date") java.time.LocalDate date,
                         @Param("inTeamId") Long inTeamId,
                         @Param("outTeamId") Long outTeamId);
}

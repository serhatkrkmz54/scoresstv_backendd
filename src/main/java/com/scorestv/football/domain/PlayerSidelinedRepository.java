package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Oyuncu sakatlik/cezalik gecmis kayitlarina erisim. */
public interface PlayerSidelinedRepository extends JpaRepository<PlayerSidelined, Long> {

    /** Tek oyuncunun tum kayitlari, yeni → eski. */
    List<PlayerSidelined> findByPlayerIdOrderByStartDateDesc(Long playerId);

    /**
     * Belirli oyuncu kumesinin AKTIF (end_date null veya bugünden büyük)
     * sakatlik/cezalik kayitlari — takim sayfasi "aktif sakatlar" listesi.
     */
    @Query("SELECT s FROM PlayerSidelined s "
            + "WHERE s.playerId IN :playerIds "
            + "  AND (s.endDate IS NULL OR s.endDate >= :today) "
            + "ORDER BY s.startDate DESC NULLS LAST")
    List<PlayerSidelined> findActiveForPlayers(
            @Param("playerIds") Collection<Long> playerIds,
            @Param("today") LocalDate today);

    /** Replace: bir oyuncunun sakatlik gecmisi tek seferde tazelenir. */
    @Modifying
    @Query("DELETE FROM PlayerSidelined s WHERE s.playerId = :playerId")
    void deleteByPlayerId(@Param("playerId") Long playerId);
}

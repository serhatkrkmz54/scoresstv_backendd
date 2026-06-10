package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Oyuncu maç istatistiklerine erişim. */
public interface FixturePlayerStatRepository
        extends JpaRepository<FixturePlayerStat, Long> {

    /**
     * Bir maçın tüm oyuncu satırları (her iki takım için), team LAZY proxy.
     * Maç detayı bunu okuyup home/away gruplar.
     */
    @Query("SELECT p FROM FixturePlayerStat p WHERE p.fixture.id = :fixtureId")
    List<FixturePlayerStat> findByFixtureId(@Param("fixtureId") Long fixtureId);

    /**
     * Replace pattern — bir maçın tüm oyuncu satırlarını siler.
     *
     * <p>{@code @Modifying @Query} ile JPQL DELETE — SQL hemen DB'ye gönderilir.
     * Spring Data'nın derived {@code deleteByXxx}'i "load-then-remove" yapar,
     * remove'lar persistence context'e kuyruklanır; sonra gelen IDENTITY-PK
     * {@code save()} INSERT'i ANINDA çalıştırır → DELETE henüz flush olmadığı
     * için UNIQUE(fixture_id, player_id) çakışır. Bu pattern o yarışı önler.
     */
    @Modifying
    @Query("DELETE FROM FixturePlayerStat p WHERE p.fixture.id = :fixtureId")
    void deleteByFixtureId(@Param("fixtureId") Long fixtureId);
}

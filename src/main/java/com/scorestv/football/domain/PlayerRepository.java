package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Oyuncu master tablosuna erisim. */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** ImageMirrorService toplu mirror icin: foto henuz aynalanmamis kayitlar. */
    List<Player> findTop200ByPhotoKeyIsNullAndPhotoUrlIsNotNull();

    /** Kapsamli (covered=true) oyuncular — DailyPlayerRefreshJob bunu kullanir. */
    List<Player> findByCoveredTrue();

    /**
     * Tam isim hidratasyonu icin: firstname / lastname henuz dolmamis ilk N
     * oyuncu (ID ascending — eski/sik gorulen player'lar oncelikli).
     *
     * <p>API-Football lineup/squad/playerStat/topscorer endpoint'leri sadece
     * kisa form ("A. Guler") doner; tam isim sadece {@code /players/profiles}
     * uzerinden gelir. Bu sorgu {@code AutoEnqueueScheduler.hourlyHydrateMissingPlayerNames}
     * tarafindan saatlik tetiklenir, batch halinde PLAYER_PROFILE_SYNC queue'sune
     * eklenir. Worker'in quota tracker'i adaptif yavaslar (75k/gun limit korunur).
     *
     * <p>Native query — empty string '' kontrolu Spring Data derived metodda
     * desteklenmez. Hem NULL hem '' (whitespace) yakalanir.
     */
    @Query(value = "SELECT * FROM players "
            + "WHERE firstname IS NULL OR firstname = '' OR TRIM(firstname) = '' "
            + "ORDER BY id ASC LIMIT 500",
            nativeQuery = true)
    List<Player> findBatchNeedingProfileHydration();
}
